# ElytronUI

ElytronUI is a Java 17 web app for opening, editing, and saving Elytron PKCS#12 credential store files. The default build produces a WAR that uses the shared Elytron core in external Tomcat.

## Run locally

Install the sibling library in your local Maven repository first. From `ElytronLib`, run `mvn clean install`.

From `ElytronUI`, use Java 17 or newer and Maven. Disable the shared Tomcat profile to build an executable WAR with bundled dependencies.

```powershell
mvn -P '!tomcat-shared-elytron' clean package

java -jar target/elytron-ui.war
```

Open `http://127.0.0.1:8080/`. The editor starts with an empty workspace. Choose a file to open or enter a filename to create one. Supply the keystore password at that point.

1. Select **Open file** to upload an existing Elytron PKCS#12 file and unlock it, or **Create keystore** to choose a filename and a new password.
2. Add, reveal, update, or delete password entries by alias.
3. Select **Save keystore** to download the encrypted file. To replace the file you opened, save the download over the original through your browser or file manager.
4. Select **Back to home** to close the keystore and return to the Open or Create screen. Confirm if you want to discard unsaved changes.

The app uses a temporary encrypted workspace for each browser session. Closing the keystore or letting the session expire removes that workspace. Save before closing to keep your changes. New stores use PKCS#12, and uploads accept Elytron PKCS#12 stores up to 16 MB. The keystore password is supplied when opening or creating a file.

## Deploy to Tomcat

Run `mvn clean package` to build `target/tomcat-shared/elytron-ui.war`. Deploy it to Tomcat 10.1 on Java 17 or newer with the shaded core in Tomcat's `lib` directory. The default context path is `/elytron-ui/`. [Tomcat deployment](../README.md#deploy-to-external-tomcat)
