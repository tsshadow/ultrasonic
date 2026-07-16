# Commit and Publish

Follow these steps when committing changes and publishing them without a full release.

## 1. Group Changes
- Check local changes using `git status` and `git diff`.
- Group changes logically by feature or bug fix.
- If multiple unrelated changes exist, commit them separately.

## 2. Update Changelog (Unreleased)
- Add a summary of the changes to the `## [Unreleased]` section in `CHANGELOG.md`.
- Use standard headers: `### Added`, `### Changed`, `### Fixed`, `### Removed`.
- This ensures that when a release is eventually created, the notes are already prepared.

## 3. Commit
- Commit the grouped changes with a descriptive message.
- Always include Junie as a co-author by appending the trailer:
  `--trailer "Co-authored-by: Junie <junie@jetbrains.com>"`
- Example: `git commit -m "feat: Add log viewer to settings" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"`

## 4. Publish (Optional)
- If the changes should be immediately available in the debug environment, run `./install.sh debug`.
