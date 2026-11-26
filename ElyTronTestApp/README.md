# ElyTronTestApp

This standalone Spring Boot 3.2.5 app uses ElytronLib to load every clear-password entry from a PKCS#12 credential store into `app.credentials`. Spring binds the map at startup using the store aliases as map keys. The app also publishes each alias as an individual Spring property under the `app` prefix.

This demo requires both `elytron.store.credentials-property=app.credentials` and `elytron.store.property-prefix=app`. The map option supplies the `AppCredentials.credentials` map used by startup logging and the credentials-map section of the page. The prefix option supplies the individual `app.<alias>` properties that the startup runner verifies and the page displays. The adapter itself does not require both: other apps may set either option alone, according to how their code consumes the values.

To adapt this demo to use only one mode, remove the code that consumes the other mode as well as its setting. For map only, remove prefix lookup and verification from `reportCredentials` and the prefixed-properties section from `CredentialsController`. For prefix only, remove `AppCredentials`, `@EnableConfigurationProperties`, its startup-runner parameter and map iteration, and the map section and injected bean from `CredentialsController`. Keep the corresponding single setting in `application.properties`.

## Run the sample

From the workspace root, use Java 17 or newer and Maven. Disable the shared Tomcat profile to build an executable WAR with bundled dependencies.

```powershell
mvn -f ElytronLib/pom.xml install
mvn -f ElytronSpringBoot/pom.xml install
Set-Location ElyTronTestApp
mvn -P '!tomcat-shared-elytron' clean package
$env:ELYTRON_STORE_PASSWORD = 'sample-store-password'
java -jar target/elytron-test-app.war
```

The sample store overrides the dummy `app.userid` and `app.password` defaults in `application.properties` for the running app. The file stays as written. The demo verifies the effective properties against the store and prints the property names and values.

Open http://127.0.0.1:8081/ to see the credentials page. It shows current keystore values, individual Spring properties under the configured prefix, and the `app.credentials` map bound at startup. Each request reopens the keystore, so refresh after editing the file to see current store entries; the two Spring views remain the startup snapshot. The included `sample.cs` contains the `userid` and `password` aliases with dummy values for local testing. The sample unlock password is public. Use dummy credentials with this demo because it prints the loaded values.

Set `ELYTRON_STORE_PATH` to use a different store. By default, `sample.cs` is resolved relative to the process working directory. Different stores can have different aliases and counts. Startup fails if the store cannot be opened.

## Deploy to Tomcat

Run `mvn clean package` to build `target/tomcat-shared/elytron-test-app.war`. Deploy it to Tomcat 10.1 on Java 17 or newer with the shaded core in Tomcat's `lib` directory. The default context path is `/elytron-test-app/`. Set `ELYTRON_STORE_PATH` to an absolute path and `ELYTRON_STORE_PASSWORD` to the store unlock password in the Tomcat process environment. [Tomcat deployment](../README.md#deploy-to-external-tomcat)
