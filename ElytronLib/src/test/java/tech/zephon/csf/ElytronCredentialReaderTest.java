package tech.zephon.csf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStoreException;

class ElytronCredentialReaderTest {
    private static final String UNLOCK_PASSWORD = "test-only-unlock-password";
    private static final String OIM_PASSWORD = "test-only-oim-password";
    private static final String JDBC_PASSWORD = "test-only-jdbc-password";

    @TempDir
    Path tempDir;

    private Path storePath;

    @BeforeEach
    void createStore() throws CredentialStoreException {
        storePath = tempDir.resolve("credentials.cs");
        var writer = ElytronStore.open(storePath, UNLOCK_PASSWORD.toCharArray(), true, true);
        writer.store("oim-rest-password", ElytronStore.password(OIM_PASSWORD.toCharArray()));
        writer.store("amps-jdbc-password", ElytronStore.password(JDBC_PASSWORD.toCharArray()));
        writer.flush();
    }

    @Test
    void readsSavedPasswordsByAlias() throws CredentialStoreException {
        var reader = ElytronCredentialReader.open(storePath, UNLOCK_PASSWORD.toCharArray());

        assertArrayEquals(OIM_PASSWORD.toCharArray(), reader.readPassword("oim-rest-password").orElseThrow());
        assertArrayEquals(JDBC_PASSWORD.toCharArray(), reader.readPassword("amps-jdbc-password").orElseThrow());
    }

    @Test
    void readsAllPasswordAliasesAndSkipsSecretKeys() throws CredentialStoreException {
        var writer = ElytronStore.open(storePath, UNLOCK_PASSWORD.toCharArray(), false, true);
        writer.store("service.api/token", ElytronStore.password("sample-token".toCharArray()));
        writer.store("encryption-key", new SecretKeyCredential(new SecretKeySpec(new byte[16], "AES")));
        writer.flush();
        var reader = ElytronCredentialReader.open(storePath, UNLOCK_PASSWORD.toCharArray());

        var credentials = reader.readPasswords();

        assertEquals(Set.of("oim-rest-password", "amps-jdbc-password", "service.api/token"), credentials.keySet());
        assertArrayEquals(OIM_PASSWORD.toCharArray(), credentials.get("oim-rest-password"));
        assertArrayEquals(JDBC_PASSWORD.toCharArray(), credentials.get("amps-jdbc-password"));
        assertArrayEquals("sample-token".toCharArray(), credentials.get("service.api/token"));
    }

    @Test
    void returnsEmptyForMissingAlias() throws CredentialStoreException {
        var reader = ElytronCredentialReader.open(storePath, UNLOCK_PASSWORD.toCharArray());

        assertTrue(reader.readPassword("missing-alias").isEmpty());
    }

    @Test
    void rejectsIncorrectUnlockPassword() {
        assertThrows(CredentialStoreException.class,
                () -> ElytronCredentialReader.open(storePath, "incorrect-test-password".toCharArray()));
    }
}
