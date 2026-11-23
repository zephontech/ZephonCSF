package tech.zephon.csf;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.password.interfaces.ClearPassword;

/** Reads password credentials from an existing Elytron PKCS#12 credential store. */
public final class ElytronCredentialReader {
    private final CredentialStore store;

    private ElytronCredentialReader(CredentialStore store) {
        this.store = store;
    }

    /**
     * Opens a read-only snapshot. Open a new reader to pick up changes on disk.
     * The caller may clear the supplied password after this method returns.
     *
     * @throws CredentialStoreException if the store cannot be opened
     */
    public static ElytronCredentialReader open(Path path, char[] unlockPassword)
            throws CredentialStoreException {
        return new ElytronCredentialReader(ElytronStore.open(path, unlockPassword, false, false));
    }

    /**
     * Returns a fresh password array, or an empty result if the alias has no clear password.
     * The caller should clear the returned array after use.
     *
     * @throws CredentialStoreException if the credential cannot be retrieved
     */
    public Optional<char[]> readPassword(String alias) throws CredentialStoreException {
        PasswordCredential credential = store.retrieve(alias, PasswordCredential.class, ClearPassword.ALGORITHM_CLEAR);
        ClearPassword password = credential == null ? null : credential.getPassword(ClearPassword.class);
        return password == null ? Optional.empty() : Optional.of(password.getPassword());
    }

    /**
     * Returns all clear passwords keyed by their Elytron alias, each in a fresh array.
     * The caller should clear the arrays after use.
     *
     * @throws CredentialStoreException if aliases or credentials cannot be retrieved
     */
    public Map<String, char[]> readPasswords() throws CredentialStoreException {
        Map<String, char[]> passwords = new HashMap<>();
        try {
            for (String alias : store.getAliases()) {
                readPassword(alias).ifPresent(password -> passwords.put(alias, password));
            }
            return passwords;
        } catch (CredentialStoreException | RuntimeException ex) {
            passwords.values().forEach(password -> Arrays.fill(password, '\0'));
            throw ex;
        }
    }
}
