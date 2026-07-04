#!/bin/bash
set -e

cd "$(dirname "$(readlink -f "$0")")"

# Handle arguments
ARG1=$(echo "${1:-debug}" | tr '[:upper:]' '[:lower:]')
INCREMENT_TYPE=""
BUILD_TYPE="debug"

case "$ARG1" in
    patch|minor|major)
        INCREMENT_TYPE="$ARG1"
        BUILD_TYPE="release"
        ;;
    release)
        BUILD_TYPE="release"
        ARG2=$(echo "${2:-}" | tr '[:upper:]' '[:lower:]')
        case "$ARG2" in
            patch|minor|major)
                INCREMENT_TYPE="$ARG2"
                ;;
            *)
                if [ -t 0 ]; then
                    echo "Release requested. Which version increment?"
                    select opt in "patch" "minor" "major" "none"; do
                        case $opt in
                            patch|minor|major) INCREMENT_TYPE=$opt; break;;
                            none) INCREMENT_TYPE=""; break;;
                            *) echo "invalid option $REPLY";;
                        esac
                    done
                else
                    echo "Non-interactive session for 'release', skipping auto-increment."
                    echo "Use './bup patch', './bup minor', or './bup major' for automatic increment."
                fi
                ;;
        esac
        ;;
    debug)
        BUILD_TYPE="debug"
        ;;
    *)
        echo "Usage: ./bup [debug|release|patch|minor|major]"
        echo "Example: ./bup patch   (Increments patch version and does release build)"
        exit 1
        ;;
esac

# Function to increment version
increment_version() {
    local type=$1
    local file="../ultrasonic/build.gradle"
    
    local current_name=$(grep "versionName" "$file" | head -n 1 | sed 's/.*"\(.*\)".*/\1/')
    local current_code=$(grep "versionCode" "$file" | head -n 1 | sed 's/[^0-9]*//g')
    
    IFS='.' read -r major minor patch <<< "$current_name"
    
    case "$type" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
    esac
    
    local new_name="$major.$minor.$patch"
    local new_code=$((current_code + 1))
    
    echo "--- Incrementing version ($type): $current_name ($current_code) -> $new_name ($new_code) ---"
    
    sed -i "s/versionCode .*/versionCode $new_code/" "$file"
    sed -i "s/versionName .*/versionName \"$new_name\"/" "$file"
    
    # Update CHANGELOG.md and RELEASE_NOTES.md using python helper
    python3 update-version.py "$new_name"
}

if [ -n "$INCREMENT_TYPE" ]; then
    increment_version "$INCREMENT_TYPE"
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
        git add -u ..
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
