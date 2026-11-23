package tech.zephon.csf;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Map;

import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.WildFlyElytronCredentialStoreProvider;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.ClearPassword;

public final class ElytronStore {
    private static final Provider STORE_PROVIDER = new WildFlyElytronCredentialStoreProvider();
    private static final Provider PASSWORD_PROVIDER = new WildFlyElytronPasswordProvider();

    private ElytronStore() {
    }

    public static CredentialStore open(Path path, char[] unlockPassword, boolean create, boolean modifiable)
            throws CredentialStoreException {
        CredentialStore store;
        try {
            store = CredentialStore.getInstance("KeyStoreCredentialStore", STORE_PROVIDER);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("The Elytron credential store provider is unavailable.", ex);
        }

        var protection = new CredentialStore.CredentialSourceProtectionParameter(
                IdentityCredentials.NONE.withCredential(password(unlockPassword)));
        store.initialize(Map.of(
                "location", path.toString(),
                "keyStoreType", "PKCS12",
                "create", Boolean.toString(create),
                "modifiable", Boolean.toString(modifiable)),
                protection, new Provider[] {PASSWORD_PROVIDER});
        return store;
    }

    public static PasswordCredential password(char[] value) {
        return new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, value));
    }
}
