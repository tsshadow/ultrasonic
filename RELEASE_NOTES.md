## [6.3.2] - 2026-07-05
### Changed
- Version bump.

## [6.3.1] - 2026-07-05
### Added
- **API Key Automation**: Automatically fetches Subsonic API keys for LMS servers during setup and connection testing.

## [6.3.0] - 2026-07-05
### Added
- **Welcome Dialog**: New users are now greeted with a setup wizard to enter login credentials.
- **Auto-Server Discovery**: Automatically configures Alpha and Stable LMS servers based on build type.
- **Connection Validation**: Real-time server ping during setup to ensure correct credentials.
- **Dynamic Playlists (Tiles)**: Support for server-side dynamic playlists synced with LMS and Music Management.
- **Debug Features**: Share and view debug logs directly from settings in debug builds.
- **ListenBrainz**: Scrobbling is now enabled by default.

### Changed
- **UI Improvements**: Significant visibility improvements to player controls, including a green pause button.
- **Landscape Player**: Optimized player layout for landscape mode with scrollable controls.
- **Tile Loading**: Delayed initial tile loading until server connection is established.
- **Deployment**: Improved APK distribution with robust fallbacks.
- **Default Settings**: 5-star rating system enabled by default.

## [6.2.0-166] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-165] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-164] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-163] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-162] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-161] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-160] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-159] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-158] - 2026-07-05
### Changed
- Version bump.

## [6.2.0-157] - 2026-07-05
### Changed
- Version bump.

## [6.2.0] - 2026-07-04
### Added
- **Spotify-style UI**: Integrated dynamic "Songs" (Green) and "Sets" (Blue) branding throughout the app.
- **Merged Views**: "Songs" and "Livesets" tabs merged into a single view with a brand-aware toggle switch.
- **OLED Black Theme**: Implemented a pure black (#000000) background for OLED displays and removed the light theme.
- **Brand Consistency**: Dynamic accent color updates for switches, sliders, chips, and scrollbars across all fragments.
- **Tooling Upgrade**: Modernized build pipeline with Gradle 9.6.0 and updated core dependencies.
- **Improved About Screen**: Detailed information on `tsshadow/lms` integration and technical build details.
- **Update System**: Enhanced auto-update checker with authentication and version tracking.

## [6.0.0-140] - 2026-07-04
### Added
- Integrated full changelog support for debug builds in the distribution pipeline.
- Added direct link to `changelog.md` in the download portal.

## [5.2.1] - 2026-07-04
### Added
- Automated version increment support to `bup` script (patch, minor, major).
- Automatic synchronization of bundled assets during release.

## [5.2.0] - 2026-07-04
### Added
- Major dependency update: All packages and Gradle are now completely up to date.
- Under the hood improvements with Gradle 8.12.1, AGP 8.7.3, and Kotlin 2.1.0.
- Script and build pipeline reorganization.

## [5.1.1] - 2026-07-04
### Changed
- Minor version bump.

## [5.1.0] - 2026-07-04
### Added
- Added auto update functionality that depends on an apk-hoster server. This will run on startup of the app or can be manually triggered via the "Check for Updates" button in the About screen.
- Highlights: A new "What's New" popup shows you the most important changes after an update.
- Changelog: The full history of changes is now accessible directly from the About screen.
