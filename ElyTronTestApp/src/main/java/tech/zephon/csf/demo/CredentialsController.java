package tech.zephon.csf.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import tech.zephon.csf.ElytronCredentialReader;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.HtmlUtils;
import org.wildfly.security.credential.store.CredentialStoreException;

@Controller
final class CredentialsController {
    private final Environment environment;
    private final ElyTronTestApp.AppCredentials credentials;

    CredentialsController(Environment environment, ElyTronTestApp.AppCredentials credentials) {
        this.environment = environment;
        this.credentials = credentials;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> credentials() {
        Path storePath = Path.of(environment.getRequiredProperty("elytron.store.path"));
        char[] unlock = environment.getRequiredProperty("elytron.store.unlock-password").toCharArray();
        try {
            var reader = ElytronCredentialReader.open(storePath, unlock);
            Map<String, char[]> passwords = reader.readPasswords();
            try {
                String prefix = environment.getRequiredProperty("elytron.store.property-prefix");
                return page(HttpStatus.OK, renderCredentials(passwords, prefix, credentials.credentials()));
            } finally {
                passwords.values().forEach(password -> Arrays.fill(password, '\0'));
            }
        } catch (CredentialStoreException ex) {
            return page(HttpStatus.SERVICE_UNAVAILABLE, "<p>Could not read the configured keystore.</p>");
        } finally {
            Arrays.fill(unlock, '\0');
        }
    }

    private String renderCredentials(Map<String, char[]> passwords, String prefix,
            Map<String, String> mappedCredentials) {
        var content = new StringBuilder();
        content.append("<h2>Current keystore values</h2>")
                .append(renderTable("Alias", "Value", passwords));

        var prefixedProperties = new TreeMap<String, String>();
        passwords.keySet().forEach(alias -> {
            String property = prefix + "." + alias;
            prefixedProperties.put(property, environment.getProperty(property, "<not set>"));
        });
        content.append("<h2>Spring properties from prefix: ").append(escape(prefix)).append("</h2>")
                .append(renderTable("Property", "Value", prefixedProperties));

        content.append("<h2>Spring credentials map (app.credentials)</h2>")
                .append(renderTable("Alias", "Value", mappedCredentials));
        return content.toString();
    }

    private static String renderTable(String keyHeading, String valueHeading, Map<String, ?> values) {
        if (values.isEmpty()) {
            return "<p>No password entries.</p>";
        }
        var table = new StringBuilder("<table><thead><tr><th scope=\"col\">")
                .append(escape(keyHeading)).append("</th><th scope=\"col\">")
                .append(escape(valueHeading)).append("</th></tr></thead><tbody>");
        new TreeMap<>(values).forEach((key, value) -> table
                .append("<tr><th scope=\"row\">").append(escape(key))
                .append("</th><td><code>").append(escape(value instanceof char[] chars
                        ? new String(chars) : String.valueOf(value)))
                .append("</code></td></tr>\n"));
        return table.append("</tbody></table>").toString();
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    private static ResponseEntity<String> page(HttpStatus status, String content) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body("""
                        <!doctype html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>Live Elytron credentials</title>
                            <style>
                                body { margin: 0; color: #172033; background: #f4f6fa; font: 1rem system-ui; }
                                main { max-width: 48rem; padding: 2rem 1rem; margin: auto; }
                                a { color: #164ca0; }
                                table { width: 100%%; border-collapse: collapse; background: white; }
                                th, td { padding: .7rem 1rem; border: 1px solid #d2d9e5; text-align: left; overflow-wrap: anywhere; }
                                code { white-space: pre-wrap; }
                            </style>
                        </head>
                        <body>
                            <main>
                                <h1>Live Elytron credentials</h1>
                                <p><a href="./">Reload from keystore</a></p>
                                %s
                            </main>
                        </body>
                        </html>
                        """.formatted(content));
    }
}
