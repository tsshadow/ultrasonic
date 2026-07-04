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

Create or update `.env` (this file must never be committed) with the
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

## Building and Deployment
If you are modifying the code and need to rebuild and redeploy the system:

1.  **Install dependencies**: `./scripts/install.sh`
2.  **Build app**: `./scripts/build.sh [debug|release]` (defaults to debug)
3.  **Publish app & hoster**: `./scripts/publish.sh [debug|release]` (defaults to debug)
4.  **Deploy to remote**: `./scripts/deploy.sh [debug|release]` (defaults to debug)
5.  **Full pipeline (Build + Publish + Deploy)**: `./install` or `./install.sh [debug|release]` (defaults to debug)
    - Use `patch`, `minor`, or `major` as mode for version increments (e.g., `./install patch`).

### APK Hoster
The `apk-hoster` service is managed in its own repository: [tsshadow/apk-hoster](https://github.com/tsshadow/apk-hoster).
Builds from this project are automatically synced to the hoster's storage if configured in `.env`.

#### Distribution and Custom Paths
You can configure where builds are saved and how they are served:
- `DIST_DIR`: Local path where APKs and `index.html` are saved (e.g., `/mnt/teun/ultrasonic-builds`).
- `REMOTE_DIST_PATH`: Path on the remote server where builds are located.

#### Remote Deployment Options
You can configure deployment in `.env`:
1.  **Portainer Webhook**: Set `PORTAINER_WEBHOOK_URL`. This can be used to notify other services of a new build.
2.  **SSH**: Set `REMOTE_HOST`, `REMOTE_USER`, etc. The script will automatically sync your APKs to the remote server using `scp`.

#### APK Hoster API
The `apk-hoster` service provides the following endpoints:
- `GET /api/version?apk=ultrasonic`: Returns JSON with the latest version info.
- `POST /api/add-apk`: Upload a new APK file.
    - Fields: `apk` (file), `release_notes` (text, optional), `password` (text, optional).
    - Headers: `X-Upload-Password` (optional alternative to `password` field).
    - Configuration: Set `UPLOAD_PASSWORD` and `ALLOWED_IPS` (comma-separated) in `.env` to secure this endpoint.

**Tip**: Use `DEPLOY_TARGET_NAME` in `.env` to give your deployment target a friendly name which will be displayed during the deployment process. The system now uses this to automatically manage stack naming and discovery across different repositories.

[subsonic]: http://www.subsonic.org/
[subapi]: http://www.subsonic.org/pages/api.jsp
[airsonic]: https://github.com/airsonic-advanced/airsonic-advanced
[supysonic]: https://github.com/spl0k/supysonic
[ampache]: https://ampache.org/
[gpl3]: https://opensource.org/licenses/gpl-3.0.html
