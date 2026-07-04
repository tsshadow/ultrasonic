#!/bin/bash
set -e

cd "$(dirname "$(readlink -f "$0")")"

# Build type (release or debug)
BUILD_TYPE=$(echo "${1:-debug}" | tr '[:upper:]' '[:lower:]')
if [[ "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "debug" ]]; then
    echo "Invalid build type: $BUILD_TYPE. Use 'release' or 'debug'."
    exit 1
fi

./build.sh "$BUILD_TYPE"
./publish.sh "$BUILD_TYPE"
./deploy.sh "$BUILD_TYPE"

if [ "$BUILD_TYPE" == "release" ]; then
    echo "--- Finalizing Release ---"
    # Extract version info
    VERSION_NAME=$(grep "versionName" ../ultrasonic/build.gradle | head -n 1 | sed 's/.*"\(.*\)".*/\1/' || echo "unknown")
    TAG="v$VERSION_NAME"

    # Commit pending changes
    if [ -n "$(git status --porcelain ..)" ]; then
        echo "Committing pending changes for release $TAG..."
        git add ..
        git commit -m "Release $TAG" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
    fi

    # Check if tag exists
    if git rev-parse "$TAG" >/dev/null 2>&1; then
        echo "Tag $TAG already exists."
    else
        echo "Creating git tag: $TAG"
        git tag -a "$TAG" -m "Release $TAG"
        echo "Successfully created tag $TAG"
    fi

    echo "Pushing changes and tags to origin..."
    git push origin develop
    git push origin "$TAG"

    # Create GitHub Release
    if command -v gh >/dev/null 2>&1; then
        echo "Creating GitHub Release for $TAG..."
        # Extract release notes for the current version
        NOTES=$(awk -v ver="$VERSION_NAME" '$0 ~ "^## \\[" ver "\\]" {flag=1; next} /^## \[/ {flag=0} flag' ../RELEASE_NOTES.md | sed '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
        
        if [ -n "$NOTES" ]; then
            echo "$NOTES" > release_notes_tmp.txt
            gh release create "$TAG" --title "Release $TAG" --notes-file release_notes_tmp.txt
            rm release_notes_tmp.txt
        else
            gh release create "$TAG" --title "Release $TAG" --notes "Official release $TAG"
        fi
        echo "GitHub Release created successfully."
    else
        echo "Warning: gh CLI not found, skipping GitHub Release creation."
    fi
fi

echo "--- $BUILD_TYPE BUP completed successfully ---"
