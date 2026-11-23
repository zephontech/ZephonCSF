package tech.zephon.csf.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.wildfly.security.credential.store.CredentialStoreException;

@RestController
@RequestMapping("/api")
final class CredentialController {
    private static final Pattern ALIAS = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}");
    private final CredentialStoreService store;

    CredentialController(CredentialStoreService store) {
        this.store = store;
    }

    @GetMapping("/credentials")
    List<String> aliases(@RequestHeader(KeystoreController.STORE_HEADER) String id) throws CredentialStoreException {
        return store.aliases(id);
    }

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    Entry create(@RequestHeader(KeystoreController.STORE_HEADER) String id, @RequestBody Create request) throws CredentialStoreException {
        String alias = alias(request.alias());
        store.save(id, alias, value(request.value()), true);
        return new Entry(alias);
    }

    @PostMapping("/credentials/{alias}/reveal")
    Revealed reveal(@RequestHeader(KeystoreController.STORE_HEADER) String id, @PathVariable String alias) throws CredentialStoreException {
        char[] secret = store.read(id, alias);
        try {
            return new Revealed(new String(secret));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @PutMapping("/credentials/{alias}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@RequestHeader(KeystoreController.STORE_HEADER) String id,
                @PathVariable String alias, @RequestBody Replacement request)
            throws CredentialStoreException {
        store.save(id, alias, value(request.value()), false);
    }

    @DeleteMapping("/credentials/{alias}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader(KeystoreController.STORE_HEADER) String id, @PathVariable String alias) throws CredentialStoreException {
        store.delete(id, alias);
    }

    private static String alias(String alias) {
        if (alias == null || !ALIAS.matcher(alias).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use 1 to 64 letters, digits, periods, underscores or hyphens. Start with a letter or digit.");
        }
        return alias.toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        if (value == null || value.isEmpty() || value.length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a credential value of 1 to 4096 characters.");
        }
        return value;
    }

    record Create(String alias, String value) {
        @Override
        public String toString() {
            return "Create[alias=" + alias + ", value=<redacted>]";
        }
    }

    record Replacement(String value) {
        @Override
        public String toString() {
            return "Replacement[value=<redacted>]";
        }
    }

    record Entry(String alias) {}

    record Revealed(String value) {
        @Override
        public String toString() {
            return "Revealed[value=<redacted>]";
        }
    }
}
