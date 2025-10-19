# Ultrasonic

Ultrasonic is free and open-source music streaming Android client for
[Subsonic][subsonic] [API][subapi] (version 1.7.0 or higher) compatible
servers.

## License

This software is licensed under the terms of the GNU General Public License
version 3 (GPLv3).

Full text of the license is available in the [LICENSE](LICENSE) file and
[online][gpl3].

## Android signing configuration

Ultrasonic now shares a single upload key between local builds and GitHub
Actions. Configure the same credentials in both environments so that release
artifacts are interchangeable.

### Local development

Create or update `local.properties` (this file must never be committed) with the
location of your keystore and its credentials:

```
# Not tracked by Git – used only on your workstation
SIGNING_STORE_FILE=keystore.jks
SIGNING_STORE_PASSWORD=***replace***
SIGNING_KEY_ALIAS=***replace***
SIGNING_KEY_PASSWORD=***replace***
```

Place the referenced `keystore.jks` in the project root or adjust the path to
match your setup. You can generate a development keystore with
`./gradlew generateKeystore` when `keytool` is available on your PATH. Debug
builds will keep using the default Android Studio debug keystore until the
shared upload key is present, so you can still run the app before finishing the
signing setup.

### GitHub Actions

Add the following repository secrets under **Settings → Secrets and variables →
Actions** so the workflow can sign release artifacts automatically:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of `keystore.jks` (`base64 -w0 keystore.jks`). |
| `ANDROID_KEYSTORE_PASSWORD` | Password protecting the keystore. |
| `ANDROID_KEY_ALIAS` | Alias of the signing key within the keystore. |
| `ANDROID_KEY_PASSWORD` | Password for the key alias. |

GitHub Actions will decode the keystore, reuse the same credentials as Android
Studio, and publish signed APK/AAB artifacts for every push, pull request, or
manual run.

[subsonic]: http://www.subsonic.org/
[subapi]: http://www.subsonic.org/pages/api.jsp
[airsonic]: https://github.com/airsonic-advanced/airsonic-advanced
[supysonic]: https://github.com/spl0k/supysonic
[ampache]: https://ampache.org/
[gpl3]: https://opensource.org/licenses/gpl-3.0.html
