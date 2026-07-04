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

## Building and Deployment
If you are modifying the code and need to rebuild and redeploy the system:

1.  **Install dependencies**: `./install.sh`
2.  **Build app**: `./build.sh`
3.  **Publish app & hoster**: `./publish.sh`
4.  **Deploy to remote**: `./deploy.sh`
5.  **Full pipeline (Build + Publish + Deploy)**: `./build_and_publish.sh`

#### Distribution and Custom Paths
You can configure where builds are saved and how they are served:
- `DIST_DIR`: Local path where APKs and `index.html` are saved (e.g., `/mnt/teun/ultrasonic-builds`).
- `REMOTE_DIST_PATH`: Path on the remote server where builds are located (used for Docker volume mounting).

#### Remote Deployment Options
You can configure deployment in `local.properties`:
1.  **Portainer Webhook**: Set `PORTAINER_WEBHOOK_URL`. This will trigger a webhook to update the `apk-hoster` service. *Note: Webhooks only trigger a redeploy/pull; they do not update environment variables. Manage variables directly in the Portainer Stack UI.*
2.  **SSH**: Set `REMOTE_HOST`, `REMOTE_USER`, etc. The script will automatically discover your Docker Compose configuration (even if managed by Portainer), transfer it securely, and redeploy the stack. If the stack does not exist yet, it will be created using the local template. This method is highly recommended for multi-host setups and Community Edition users.

**Tip**: Use `DEPLOY_TARGET_NAME` in `local.properties` to give your deployment target a friendly name which will be displayed during the deployment process. The system now uses this to automatically manage stack naming and discovery across different repositories.

[subsonic]: http://www.subsonic.org/
[subapi]: http://www.subsonic.org/pages/api.jsp
[airsonic]: https://github.com/airsonic-advanced/airsonic-advanced
[supysonic]: https://github.com/spl0k/supysonic
[ampache]: https://ampache.org/
[gpl3]: https://opensource.org/licenses/gpl-3.0.html
