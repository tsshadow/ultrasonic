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

    # Verify release notes
    if grep -q "## \[$VERSION_NAME\]" ../RELEASE_NOTES.md; then
        echo "Found release notes for v$VERSION_NAME"
    else
        echo "WARNING: Release notes for v$VERSION_NAME not found in RELEASE_NOTES.md"
    fi
    
    # Check if tag exists
    if git rev-parse "$TAG" >/dev/null 2>&1; then
        echo "Tag $TAG already exists."
    else
        echo "Creating git tag: $TAG"
        git tag -a "$TAG" -m "Release $TAG"
        echo "Successfully created tag $TAG"
        echo "Note: Don't forget to push tags: git push origin --tags"
    fi
fi

echo "--- $BUILD_TYPE BUP completed successfully ---"
