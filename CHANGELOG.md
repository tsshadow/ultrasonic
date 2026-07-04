# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [5.1.0] - 2026-07-04
### Added
- Created `scripts/deploy-stack.sh`, a generalized Docker stack deployment tool supporting Portainer discovery, SSH transfer, and intelligent fallbacks.
- Support for initial stack creation: the deployment script now automatically creates the stack if it does not exist yet using a local template.
- Persistent Build Directory: Configured the build system to use a shared folder (`/mnt/teun/ultrasonic-builds`) for all APKs and distribution files.
- Generic Docker Image: The `apk-hoster` Docker image is now generic and uses volume mounts for content, avoiding the need to rebuild the image for every new build.
- Auto-update system: Implemented `UpdateChecker` polling `apk-hoster` API on startup.
- Integrated Changelog: Bundled `CHANGELOG.md` and `RELEASE_NOTES.md` into assets; added `Util.showChangelog` for in-app viewing.
- Version Tracking: Added `last_seen_version` to `Settings` for automated update detection and release notes popup.
- Modular build system: `build.sh`, `publish.sh`, and `deploy.sh`.
- Versioned Docker tagging: the `apk-hoster` image is now tagged with both `:latest` and the version name from Gradle (e.g., `:5.1.0`).
- Portainer Webhook support for automated Stack updates.
- Automatic Docker group permission handling via `sg docker` re-execution.
- Integrated `apk-hoster` deployment into the main pipeline.
- `bup` shortcut for full build-publish-deploy workflow.
- Registry authentication pre-checks for Docker Hub.

### Changed
- **Deployment**: Refactored `deploy.sh` to use the new generalized `deploy-stack.sh` helper, simplifying per-project deployment scripts.
- **Deployment**: Enhanced stack discovery to support `PORTAINER_HOST` for pulling master templates.
- **Deployment**: Improved robustness with `base64` template transfer and automated tab-to-space conversion in compose files.
- Migrated all build and deployment configurations to `local.properties`.
- Updated `install.sh` for automated SDK and dependency management.
- Standardized deployment variable names for cross-project compatibility.

### Fixed
- Fixed deployment failure when no existing stack configuration is found on the remote host by providing a local `docker-compose.yml` fallback.
- Fixed empty webpage in `apk-hoster` by bundling the `dist` directory into the Docker image.
- Fixed APK files downloading as ZIP by registering correct MIME types in `apk-hoster`.
- Fixed local.properties loading logic to skip invalid bash identifiers.
- Fixed non-interactive session hangs in `publish.sh`.

## [5.0.1] - 2026-07-02
### Added
- 'Check for Updates' button in the About screen.
### Fixed
- Patch for initial testing of updated deployment scripts.
