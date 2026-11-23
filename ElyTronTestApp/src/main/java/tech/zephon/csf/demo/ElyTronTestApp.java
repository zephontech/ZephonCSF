package tech.zephon.csf.demo;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableConfigurationProperties(ElyTronTestApp.AppCredentials.class)
public class ElyTronTestApp extends SpringBootServletInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(ElyTronTestApp.class);

    public static void main(String[] args) {
        SpringApplication.run(ElyTronTestApp.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ElyTronTestApp.class);
    }

    @Bean
    ApplicationRunner reportCredentials(AppCredentials credentials, Environment environment) {
        return args -> {
            String prefix = environment.getRequiredProperty("elytron.store.property-prefix");
            credentials.credentials().forEach((alias, value) -> {
                String property = prefix + "." + alias;
                String effectiveValue = environment.getProperty(property);
                if (!value.equals(effectiveValue)) {
                    throw new IllegalStateException("Elytron did not supply the effective value for " + property);
                }
                LOG.info("{}={}", property, effectiveValue);
            });
            LOG.info("Loaded {} password entries and verified their startup property values.",
                    credentials.credentials().size());
        };
    }

    @ConfigurationProperties("app")
    public record AppCredentials(Map<String, String> credentials) {
        public AppCredentials {
            if (credentials == null) {
                throw new IllegalStateException("Elytron did not publish app.credentials. Check the ElytronLib "
                        + "version and set elytron.store.credentials-property=app.credentials.");
            }
            credentials = Map.copyOf(credentials);
            System.out.println("creds:" + credentials);
        }

        @Override
        public String toString() {
            return "AppCredentials[entries=" + credentials.size() + "]";
        }
    }
}
