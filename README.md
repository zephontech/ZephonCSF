# ZephonCSF

ZephonCSF uses the same Elytron PKCS#12 credential-store file for three tasks. ElytronUI creates and edits it, ElytronLib reads password entries by alias, and ElyTronTestApp demonstrates two ways to consume those entries in Spring Boot.

| Project | Role | Output |
| --- | --- | --- |
| [ElytronLib](ElytronLib/README.md) | Read credentials or create and edit Elytron stores. The shaded JAR contains the WildFly runtime. | `tech.zephon.csf:elytron-core-all:1.2.0-SNAPSHOT` |
| [ElytronSpringBoot](ElytronLib/README.md#load-spring-boot-properties-at-startup) | Load keystore values into Spring properties at startup. | `tech.zephon.csf:elytron-spring-boot:1.2.0-SNAPSHOT` |
| [ElytronUI](ElytronUI/README.md) | Open, create, edit, and download credential stores in a browser. | `ElytronUI/target/tomcat-shared/elytron-ui.war` |
| [ElyTronTestApp](ElyTronTestApp/README.md) | Show startup property overrides and read the keystore again on demand. | `ElyTronTestApp/target/tomcat-shared/elytron-test-app.war` |

Each project has its own Maven POM. Both apps use Spring Boot 3.2.5 and build WARs for external Tomcat with a shared Elytron core by default. ElytronUI uses the shaded core directly. ElyTronTestApp uses the core together with the Spring Boot adapter.

## Build and run

Use Java 17 or newer and Maven. On the Windows test workstation, load Java 17 with `. $HOME\setjava17.ps1`. From the workspace root, install the core and Spring Boot adapter, then build both apps. Maven activates `tomcat-shared-elytron` automatically and writes the WARs under each app's `target/tomcat-shared/`.

```powershell
mvn -f ElytronLib/pom.xml clean install
mvn -f ElytronSpringBoot/pom.xml clean install
mvn -f ElytronUI/pom.xml clean package
mvn -f ElyTronTestApp/pom.xml clean package
```

### Run locally with embedded Tomcat

Disable the shared profile to build a WAR with bundled dependencies. To run the editor, start in a PowerShell window at the workspace root.

```powershell
Set-Location ElytronUI
mvn -P '!tomcat-shared-elytron' clean package
java -jar target/elytron-ui.war --server.address=127.0.0.1
```

Open `http://127.0.0.1:8080/`. The editor starts with an empty workspace. Create or upload a store, edit its password aliases, and choose **Save keystore** to download the updated file. The editor works on a temporary copy for each browser session. Closing the store or letting the session expire clears that copy, so save before closing.

To run the demo, use a separate PowerShell window at the workspace root.

```powershell
Set-Location ElyTronTestApp
mvn -P '!tomcat-shared-elytron' clean package
$env:ELYTRON_STORE_PASSWORD = 'sample-store-password'
java -jar target/elytron-test-app.war --server.address=127.0.0.1
```

Open `http://127.0.0.1:8081/`. The demo reads [sample.cs](ElyTronTestApp/sample.cs) by default, relative to its working directory. The file contains dummy `userid` and `password` entries. The unlock password opens the file, and the password entry is a separate value stored under its alias. The demo prints the entries at startup and displays them in the browser. Both apps bind to localhost when run with embedded Tomcat.

## Deploy to external Tomcat

Use Tomcat 10.1 with Java 17 or newer. `CATALINA_BASE` is the Tomcat instance directory. For the shared deployment, Tomcat loads one shaded Elytron core JAR from `CATALINA_BASE/lib` and both applications use it. The demo WAR includes the Spring Boot adapter that reads the keystore at startup. [Tomcat class loader guide](https://tomcat.apache.org/tomcat-10.1-doc/class-loader-howto.html)

### Share the Elytron core across WARs

Build the shared core and both WARs using the default Maven commands in [Build and run](#build-and-run).

1. Stop Tomcat before replacing the shared JAR or either application.
2. If upgrading from the loose-JAR deployment, move the previous WildFly Elytron and supporting JBoss JARs installed for Elytron out of `CATALINA_BASE/lib`. Back up any previous shaded core JAR, the two old WARs, and their expanded `webapps/elytron-ui` and `webapps/elytron-test-app` directories outside Tomcat's library and deployment directories.
3. Copy the new shaded core into `lib` and the two profile WARs into `webapps`. The following PowerShell commands use the Windows test instance. On Linux, copy the same artifacts into `$CATALINA_BASE/lib` and `$CATALINA_BASE/webapps`.

   ```powershell
   $env:CATALINA_BASE = 'C:\Transfer\New\tomcat10'
   Copy-Item ElytronLib/target/elytron-core-all-1.2.0-SNAPSHOT.jar "$env:CATALINA_BASE\lib\"
   Copy-Item ElytronUI/target/tomcat-shared/elytron-ui.war "$env:CATALINA_BASE\webapps\"
   Copy-Item ElyTronTestApp/target/tomcat-shared/elytron-test-app.war "$env:CATALINA_BASE\webapps\"
   ```

### Configure and start Tomcat

ElyTronTestApp needs an absolute path to the credential-store file and its unlock password at startup. Keep the store at a path the Tomcat process can read. ElytronUI starts with an empty workspace and gets the store from the browser when the user opens or creates one.

For Windows testing, pass the demo settings as JVM `-D` options through `CATALINA_OPTS`. This example uses the repository's sample store and public unlock password. Run it in the same PowerShell session as the copy commands.

When Tomcat supplies `elytron.store.path` and `elytron.store.unlock-password` at startup, the application does not need duplicate values for those two properties in its `application.properties`. They must still be present in Tomcat's runtime environment. The app also uses them to reopen the store for each live-page request.

```powershell
$env:CATALINA_OPTS += ' -Delytron.store.path=E:\ZephonCSF\ElyTronTestApp\sample.cs -Delytron.store.unlock-password=sample-store-password'
& "$env:CATALINA_BASE\bin\startup.bat"
```

For the Linux deployment, put the settings in `$CATALINA_BASE/bin/setenv.sh`. Tomcat's startup scripts source this file. Replace the example paths with the deployed store and unlock secret paths, and give the Tomcat account read access to both files.

```sh
export ELYTRON_STORE_PATH='/etc/elytron/testapp.cs'
ELYTRON_STORE_PASSWORD="$(cat /run/secrets/elytron-test-app-unlock)"
export ELYTRON_STORE_PASSWORD
```

Start the instance with `$CATALINA_HOME/bin/startup.sh`. If the instance directory differs from the installation directory, export `CATALINA_BASE` before starting it.

With the default connector on port `8080`, open `http://127.0.0.1:8080/elytron-ui/` and `http://127.0.0.1:8080/elytron-test-app/`. Confirm that the editor opens to its empty workspace and the demo lists the expected aliases. The demo startup log reports the number of password entries loaded into `app.credentials`. Use dummy values for the demo store because the app prints and displays them.

For a Tomcat deployment with bundled dependencies, build the WARs as shown in [Run locally with embedded Tomcat](#run-locally-with-embedded-tomcat) and copy them from each app's `target/` into `webapps`. Use the same startup settings and context paths.

## How the startup properties work

The demo includes dummy defaults for `app.userid` and `app.password`. This configuration loads the values that replace those defaults in the running Spring environment. [Demo configuration](ElyTronTestApp/src/main/resources/application.properties)

```properties
elytron.store.enabled=true
elytron.store.path=sample.cs
elytron.store.unlock-password=${ELYTRON_STORE_PASSWORD:}
elytron.store.credentials-property=app.credentials
elytron.store.property-prefix=app
```

1. Spring Boot discovers ElytronSpringBoot's `ElytronEnvironmentPostProcessor` through `META-INF/spring.factories`. The opt-in setting `elytron.store.enabled=true` activates it. [Registration](ElytronSpringBoot/src/main/resources/META-INF/spring.factories)
2. After Spring loads its configuration files and before it binds application properties, the post-processor opens the store and reads the password entries. It adds the resulting property source at the front of the Spring environment, giving the store values precedence over file defaults and command-line values for the properties it supplies. [Post-processor](ElytronSpringBoot/src/main/java/tech/zephon/csf/spring/ElytronEnvironmentPostProcessor.java)
3. With both bulk settings enabled, Spring gets a map at `app.credentials` and an individual property for each alias. For example, `userid` becomes the map key `userid` and the property `app.userid`. The sample binds the map into `AppCredentials` and checks that the individual property values match. [Demo application](ElyTronTestApp/src/main/java/tech/zephon/csf/demo/ElyTronTestApp.java)

Other Spring Boot apps can use either bulk setting independently. Reserve the map's property name and its nested names for the map when combining modes. If both bulk settings are omitted, ElytronLib maps two aliases to two properties. The defaults map `userid` and `password` to `app.userid` and `app.password`. Customize them with the four alias and destination settings. [Spring Boot settings](ElytronLib/README.md#load-spring-boot-properties-at-startup)

## How the live page reads changes

The startup properties are a snapshot taken when Spring starts. The demo's `GET /` controller opens a new read-only reader on each request, loads the current aliases and values, and renders them in a table. Select **Reload from keystore** to see changes on disk. An empty store shows an empty state. An unreadable store returns HTTP 503. [Credentials controller](ElyTronTestApp/src/main/java/tech/zephon/csf/demo/CredentialsController.java)

To try both paths side by side:

1. From the demo directory, copy `sample.cs` to `live-demo.cs` and set `$env:ELYTRON_STORE_PATH = (Resolve-Path .\live-demo.cs).Path` before starting the demo. Keep using the sample unlock password.
2. In ElytronUI, open `live-demo.cs`, change one value or add an alias, then choose **Save keystore** and replace `live-demo.cs` with the downloaded file.
3. Reload the demo page to see the edited values. Restart the demo to load that version of the store into its startup properties.

## Read credentials in Java

Add the ElytronLib Maven dependency to your application. Pass a store path and its unlock password to `ElytronCredentialReader.open(...)`. Use `readPassword(alias)` for a single value. It returns `Optional.empty()` when the alias has no readable password. Use `readPasswords()` to retrieve a `Map<String, char[]>` of all clear-password entries. Its size counts those entries, and cryptographic keys such as AES keys are skipped. [Library API and dependency](ElytronLib/README.md)

```java
var reader = ElytronCredentialReader.open(storePath, unlockPassword);
var passwords = reader.readPasswords();
try {
    System.out.println("Password entries: " + passwords.size());
    for (String alias : passwords.keySet()) {
        System.out.println(alias);
    }
} finally {
    passwords.values().forEach(value -> Arrays.fill(value, '\0'));
}
```

The caller owns the returned arrays and can clear them after use. Clear the supplied unlock array when you finish opening readers. Reuse a reader for multiple aliases from one snapshot and create a new reader to see changes on disk. An incorrect unlock password or unreadable file throws `CredentialStoreException`. [Credential reader](ElytronLib/src/main/java/tech/zephon/csf/ElytronCredentialReader.java)

## Verify and troubleshoot

- Run `mvn -f ElytronLib/pom.xml test` to verify credential reads and invalid unlock passwords. After installing the core, run `mvn -f ElytronSpringBoot/pom.xml test` to verify Spring property overrides.
- If the demo reports that `app.credentials` is missing, check `elytron.store.enabled` and `elytron.store.credentials-property`. Reinstall the core and adapter, rebuild the demo, and reload the Maven project if running from an IDE. A readable store with zero password entries reports a count of zero.
- If the store cannot be opened, check the working directory or `ELYTRON_STORE_PATH` and the store unlock password. That password is the value of `ELYTRON_STORE_PASSWORD` in the demo setup.
- If Java fails while closing a cached dependency JAR in the Codex sandbox, repeat the build from a normal PowerShell session. On this workstation, `. $HOME\setjava17.ps1` sets up the Java 17 and Maven paths for that shell.
