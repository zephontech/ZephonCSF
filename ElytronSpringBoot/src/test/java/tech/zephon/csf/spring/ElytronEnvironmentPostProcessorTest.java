package tech.zephon.csf.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;

import tech.zephon.csf.ElytronStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.wildfly.security.credential.store.CredentialStoreException;

class ElytronEnvironmentPostProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void overridesIndividualPropertiesAndPublishesTheMap() throws CredentialStoreException {
        Path store = tempDir.resolve("credentials.cs");
        char[] unlock = "test-store-password".toCharArray();
        var writer = ElytronStore.open(store, unlock, true, true);
        writer.store("userid", ElytronStore.password("test-user".toCharArray()));
        writer.store("password", ElytronStore.password("test-password".toCharArray()));
        writer.store("service.api/token", ElytronStore.password("test-token".toCharArray()));
        writer.flush();

        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testDefaults", Map.of(
                "elytron.store.enabled", "true",
                "elytron.store.path", store.toString(),
                "elytron.store.unlock-password", "test-store-password",
                "elytron.store.credentials-property", "app.credentials",
                "elytron.store.property-prefix", "app",
                "app.userid", "fallback-user",
                "app.password", "fallback-password",
                "app.service.api/token", "fallback-token")));

        new ElytronEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertEquals("test-user", environment.getProperty("app.userid"));
        assertEquals("test-password", environment.getProperty("app.password"));
        assertEquals("test-token", environment.getProperty("app.service.api/token"));
        assertEquals(Map.of("userid", "test-user", "password", "test-password", "service.api/token", "test-token"),
                environment.getProperty("app.credentials", Map.class));
    }
}
