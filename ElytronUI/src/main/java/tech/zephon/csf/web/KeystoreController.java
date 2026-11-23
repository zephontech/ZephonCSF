package tech.zephon.csf.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/store")
final class KeystoreController {
    static final String STORE_HEADER = "X-Keystore-Id";
    private static final Pattern FILENAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._ -]{0,119}");

    private final CredentialStoreService store;

    KeystoreController(CredentialStoreService store) {
        this.store = store;
    }

    @GetMapping
    ResponseEntity<CredentialStoreService.StoreInfo> current() {
        var info = store.info();
        return info == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(info);
    }

    @PostMapping("/new")
    @ResponseStatus(HttpStatus.CREATED)
    CredentialStoreService.StoreInfo create(@RequestBody CreateStore form) throws IOException {
        return store.create(filename(form.filename()), form.password());
    }

    @PostMapping(value = "/open", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CredentialStoreService.StoreInfo open(@RequestParam MultipartFile file, @RequestParam String password)
            throws IOException {
        String suppliedName = file.getOriginalFilename();
        if (suppliedName == null || suppliedName.isEmpty() || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a keystore file.");
        }
        String normalized = suppliedName.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1);
        try (var input = file.getInputStream()) {
            return store.upload(filename(leaf), password, input);
        }
    }

    @PostMapping("/file")
    ResponseEntity<byte[]> download(@RequestHeader(STORE_HEADER) String id) throws IOException {
        var download = store.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(download.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@RequestHeader(STORE_HEADER) String id) {
        store.close(id);
    }

    private static String filename(String filename) {
        if (filename == null || !FILENAME.matcher(filename).matches()
                || filename.endsWith(".") || filename.endsWith(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a filename of up to 120 letters, digits, spaces, periods, underscores or hyphens.");
        }
        return filename;
    }

    record CreateStore(String filename, String password) {
        @Override
        public String toString() {
            return "CreateStore[password=<redacted>]";
        }
    }
}
