package tech.zephon.csf.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import tech.zephon.csf.ElytronStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.server.ResponseStatusException;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.password.interfaces.ClearPassword;

@Service
@SessionScope
class CredentialStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(CredentialStoreService.class);

    private Workspace workspace;

    synchronized StoreInfo info() {
        return workspace == null ? null : workspace.info();
    }

    synchronized StoreInfo create(String filename, String password) throws IOException {
        requireClosed();
        Workspace candidate = newWorkspace(filename, password, true);
        try {
            ElytronStore.open(candidate.file, candidate.password, true, true).flush();
            workspace = candidate;
            return candidate.info();
        } catch (CredentialStoreException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not create a PKCS#12 keystore with that password.", ex);
        } finally {
            if (workspace != candidate) {
                candidate.release();
            }
        }
    }

    synchronized StoreInfo upload(String filename, String password, InputStream source) throws IOException {
        requireClosed();
        Workspace candidate = newWorkspace(filename, password, false);
        boolean opened = false;
        try {
            if (source.read() != 0x30) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choose an Elytron PKCS#12 keystore file.");
            }
            try (var output = Files.newOutputStream(candidate.file)) {
                output.write(0x30);
                source.transferTo(output);
            }
            candidate.open(false);
            workspace = candidate;
            opened = true;
            return candidate.info();
        } catch (CredentialStoreException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not open the file. Check that it is an Elytron PKCS#12 keystore and that the password is correct.",
                    ex);
        } finally {
            if (!opened) {
                candidate.release();
            }
        }
    }

    synchronized Download download(String id) throws IOException {
        Workspace active = requireWorkspace(id);
        byte[] content = Files.readAllBytes(active.file);
        active.modified = false;
        return new Download(active.filename, content);
    }

    synchronized void close(String id) {
        if (workspace == null) {
            return;
        }
        requireWorkspace(id);
        releaseWorkspace();
    }

    @PreDestroy
    synchronized void releaseWorkspace() {
        if (workspace != null) {
            workspace.release();
            workspace = null;
        }
    }

    synchronized List<String> aliases(String id) throws CredentialStoreException {
        CredentialStore store = requireWorkspace(id).open(false);
        var aliases = new TreeSet<String>();
        for (String alias : store.getAliases()) {
            PasswordCredential credential = store.retrieve(alias, PasswordCredential.class);
            if (credential != null && credential.getPassword(ClearPassword.class) != null) {
                aliases.add(alias);
            }
        }
        return List.copyOf(aliases);
    }

    synchronized char[] read(String id, String alias) throws CredentialStoreException {
        ClearPassword password = clearPassword(requireWorkspace(id).open(false), alias);
        if (password == null) {
            throw missing();
        }
        return password.getPassword();
    }

    synchronized void save(String id, String alias, String value, boolean create) throws CredentialStoreException {
        Workspace active = requireWorkspace(id);
        CredentialStore store = active.open(true);
        if (create && store.getAliases().contains(alias)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That alias already exists.");
        }
        if (!create && clearPassword(store, alias) == null) {
            throw missing();
        }

        char[] secret = value.toCharArray();
        try {
            store.store(alias, ElytronStore.password(secret));
            store.flush();
            active.modified = true;
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    synchronized void delete(String id, String alias) throws CredentialStoreException {
        Workspace active = requireWorkspace(id);
        CredentialStore store = active.open(true);
        if (clearPassword(store, alias) == null) {
            throw missing();
        }
        store.remove(alias, PasswordCredential.class);
        store.flush();
        active.modified = true;
    }

    private static ClearPassword clearPassword(CredentialStore store, String alias)
            throws CredentialStoreException {
        PasswordCredential credential = store.retrieve(alias, PasswordCredential.class);
        return credential == null ? null : credential.getPassword(ClearPassword.class);
    }

    private Workspace requireWorkspace(String id) {
        if (workspace == null || !workspace.id.equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The open keystore changed. Reload the page before continuing.");
        }
        return workspace;
    }

    private void requireClosed() {
        if (workspace != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Close the current keystore before opening another.");
        }
    }

    private static Workspace newWorkspace(String filename, String password, boolean modified) throws IOException {
        if (password == null || password.isBlank() || password.length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a keystore password of 1 to 256 characters.");
        }
        Path folder = Files.createTempDirectory("elytron-editor-");
        return new Workspace(filename, folder.resolve("store.cs"), password.toCharArray(), modified);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "That password alias was not found.");
    }

    record StoreInfo(String id, String filename, boolean modified) {}
    record Download(String filename, byte[] content) {}

    private static final class Workspace {
        private final String id = UUID.randomUUID().toString();
        private final String filename;
        private final Path file;
        private final char[] password;
        private boolean modified;

        private Workspace(String filename, Path file, char[] password, boolean modified) {
            this.filename = filename;
            this.file = file;
            this.password = password;
            this.modified = modified;
        }

        private StoreInfo info() {
            return new StoreInfo(id, filename, modified);
        }

        private CredentialStore open(boolean modifiable) throws CredentialStoreException {
            return ElytronStore.open(file, password, false, modifiable);
        }

        private void release() {
            Arrays.fill(password, '\0');
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.getParent());
            } catch (IOException ex) {
                LOG.warn("Unable to remove a temporary keystore workspace.");
            }
        }
    }
}
