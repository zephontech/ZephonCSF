package tech.zephon.csf.spring;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import tech.zephon.csf.ElytronCredentialReader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.wildfly.security.credential.store.CredentialStoreException;

public final class ElytronEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("elytron.store.enabled", Boolean.class, false)) {
            return;
        }

        Path storePath = Path.of(environment.getRequiredProperty("elytron.store.path"));
        String unlockPassword = environment.getProperty("elytron.store.unlock-password", "");
        if (unlockPassword.isBlank()) {
            throw new IllegalStateException("Set elytron.store.unlock-password to unlock the Elytron credential store.");
        }

        char[] unlock = unlockPassword.toCharArray();
        try {
            var reader = ElytronCredentialReader.open(storePath, unlock);
            var properties = readProperties(environment, reader);
            environment.getPropertySources().addFirst(new MapPropertySource("elytronCredentials", properties));
        } catch (CredentialStoreException ex) {
            throw new IllegalStateException("Could not load credentials from the Elytron store at " + storePath, ex);
        } finally {
            Arrays.fill(unlock, '\0');
        }
    }

    private static Map<String, Object> readProperties(ConfigurableEnvironment environment,
            ElytronCredentialReader reader) throws CredentialStoreException {
        String credentialsProperty = environment.getProperty("elytron.store.credentials-property");
        String propertyPrefix = environment.getProperty("elytron.store.property-prefix");
        if (credentialsProperty == null && propertyPrefix == null) {
            return readMappedProperties(environment, reader);
        }

        Map<String, String> credentials = readCredentials(reader);
        Map<String, Object> properties = new HashMap<>();
        if (credentialsProperty != null) {
            properties.put(credentialsProperty, credentials);
        }
        if (propertyPrefix != null) {
            credentials.forEach((alias, value) -> {
                String property = propertyPrefix + "." + alias;
                if (credentialsProperty != null && (property.equals(credentialsProperty)
                        || property.startsWith(credentialsProperty + ".")
                        || property.startsWith(credentialsProperty + "["))) {
                    throw new IllegalStateException("Elytron property " + property
                            + " overlaps the credentials map at " + credentialsProperty);
                }
                properties.put(property, value);
            });
        }
        return properties;
    }

    private static Map<String, Object> readMappedProperties(ConfigurableEnvironment environment,
            ElytronCredentialReader reader) throws CredentialStoreException {
        String useridAlias = environment.getProperty("elytron.store.userid-alias", "userid");
        String passwordAlias = environment.getProperty("elytron.store.password-alias", "password");
        String useridProperty = environment.getProperty("elytron.store.userid-property", "app.userid");
        String passwordProperty = environment.getProperty("elytron.store.password-property", "app.password");
        return Map.of(useridProperty, readValue(reader, useridAlias),
                passwordProperty, readValue(reader, passwordAlias));
    }

    private static Map<String, String> readCredentials(ElytronCredentialReader reader)
            throws CredentialStoreException {
        Map<String, char[]> passwords = reader.readPasswords();
        try {
            Map<String, String> credentials = new HashMap<>();
            passwords.forEach((alias, password) -> credentials.put(alias, new String(password)));
            return Map.copyOf(credentials);
        } finally {
            passwords.values().forEach(password -> Arrays.fill(password, '\0'));
        }
    }

    private static String readValue(ElytronCredentialReader reader, String alias) throws CredentialStoreException {
        char[] value = reader.readPassword(alias)
                .orElseThrow(() -> new IllegalStateException("Missing Elytron password entry for alias: " + alias));
        try {
            return new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }
}
