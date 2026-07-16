# Create Release

Follow these steps when asked to create a release.

## 1. Analyze Changes
- Inspect the diff between the current state and the last git tag (or since the last release).
- Categorize changes:
  - **Breaking changes**: Any change that breaks backward compatibility or introduces major architectural shifts. -> **Major**
  - **New Features**: Backwards compatible new functionality. -> **Minor**
  - **Fixes**: Bug fixes, performance improvements, or internal refactors without new features. -> **Patch**

## 2. Prepare Features
- If there are uncommitted changes, group them by feature/bug.
- Commit them separately with descriptive messages and the Junie co-author trailer.
- This ensures the git history clearly shows individual features before the release commit.

## 3. Update Changelog
- Ensure `CHANGELOG.md` has a `## [Unreleased]` section.
- Add the categorized changes to the `## [Unreleased]` section.
- Group them by standard headers: `### Added`, `### Changed`, `### Fixed`, `### Removed`.
- Use a concise and descriptive style, following the existing entries in `CHANGELOG.md`.

## 4. Run Release Script
- Run `./install.sh [major|minor|patch]` based on the analysis in step 1.
- **IMPORTANT**: The script will automatically:
  - Increment the version in `build.gradle`.
  - Move `[Unreleased]` notes to the new version section in `CHANGELOG.md` and `RELEASE_NOTES.md`.
  - Build, publish, and deploy.
  - Create a git commit ("Release vX.Y.Z") and tag.
  - Push changes and tags to origin.
  - Create a GitHub release if `gh` is available.
