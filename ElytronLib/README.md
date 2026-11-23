# Elytron Credential Reader

This Java 17 library reads clear-password entries by alias from Elytron PKCS#12 credential stores, including files created by ElytronUI. It uses WildFly Elytron directly and works in plain Java and Spring Boot applications.

## Add the dependency

From the `ElytronLib` directory, use Java 17 or newer and Maven to install the library in your local Maven repository.

```powershell
mvn clean install
```

Add this dependency to the consuming app's POM.

```xml
<dependency>
    <groupId>tech.zephon.csf</groupId>
    <artifactId>elytron-core-all</artifactId>
    <version>1.2.0-SNAPSHOT</version>
</dependency>
```

## Read a password

This example receives the keystore path and unlock password from the calling application.

```java
import java.nio.file.Path;
import java.util.Arrays;

import tech.zephon.csf.ElytronCredentialReader;
import org.wildfly.security.credential.store.CredentialStoreException;

static char[] readDatabasePassword(Path storePath, char[] unlockPassword)
        throws CredentialStoreException {
    try {
        return ElytronCredentialReader.open(storePath, unlockPassword)
                .readPassword("database-password")
                .orElseThrow(() -> new IllegalStateException("Missing database-password alias"));
    } finally {
        Arrays.fill(unlockPassword, '\0');
    }
}
```

The caller owns the returned array and should clear it with `Arrays.fill(password, '\0')` after use. If an alias has no clear-password entry, `readPassword` returns `Optional.empty()`. An unreadable store or incorrect unlock password throws `CredentialStoreException`.

The reader opens a read-only snapshot of the file. Reuse it to read multiple aliases, and open a new reader after replacing or updating the store.

## Read all password entries

`readPasswords()` returns a `Map<String, char[]>` keyed by the Elytron aliases. Use `size()` to count the clear-password entries and iterate the map to process them. The map contains clear passwords. The caller can clear each value array after use.

## Load Spring Boot properties at startup

Add the adapter dependency alongside the core dependency in a Spring Boot 3.2.5 app.

```xml
<dependency>
    <groupId>tech.zephon.csf</groupId>
    <artifactId>elytron-spring-boot</artifactId>
    <version>1.2.0-SNAPSHOT</version>
</dependency>
```

The adapter runs in the application. When Tomcat supplies the shaded core through its `lib` directory, declare the core dependency with `<scope>provided</scope>` in the WAR project. Install the adapter after building and installing the core using `mvn -f ElytronSpringBoot/pom.xml clean install` from the workspace root. [Shared Tomcat deployment](../README.md#share-the-elytron-core-across-wars)

Enable the startup integration in `application.properties`.

```properties
elytron.store.enabled=true
elytron.store.path=/path/to/credentials.cs
elytron.store.unlock-password=${ELYTRON_STORE_PASSWORD}
```

The library reads the `userid` and `password` aliases into `app.userid` and `app.password` before configuration binding. The store values take precedence for those properties. Set `ELYTRON_STORE_PASSWORD` in the application's environment before starting it.

To use different aliases or write to different application properties, set `elytron.store.userid-alias`, `elytron.store.password-alias`, `elytron.store.userid-property`, and `elytron.store.password-property` as needed. For example, the destination properties can be `spring.datasource.username` and `spring.datasource.password`.

For a dynamic set of aliases, add `elytron.store.credentials-property=app.credentials`. In this mode the library publishes one map containing all clear-password entries. Bind it with `@ConfigurationProperties("app")` and a `Map<String, String> credentials` member. Reserve the configured property for this map.

Set `elytron.store.property-prefix=app` to publish each alias as an individual property. For example, the `userid` alias supplies `app.userid` and overrides any existing value at startup. You can use this setting with the credentials map. Keep the map property and its nested names reserved for that map.

When both bulk settings are omitted, the library uses the two-property configuration above.

The integration is disabled by default. Plain Java consumers can use `ElytronCredentialReader` directly from the shaded core.
