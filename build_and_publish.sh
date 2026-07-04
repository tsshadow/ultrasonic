#!/bin/bash
set -e

# Configuration
APP_NAME="ultrasonic"
DIST_DIR="dist"
MODULE="ultrasonic"

# Try to find JAVA_HOME and ANDROID_HOME if not set
if [ -z "$JAVA_HOME" ] || [ -z "$ANDROID_HOME" ]; then
    echo "Note: Environment variables not set, attempting to load defaults from install.sh..."
    [ -d "$HOME/.local/jdk-21" ] && export JAVA_HOME="$HOME/.local/jdk-21"
    [ -d "$HOME/Android/Sdk" ] && export ANDROID_HOME="$HOME/Android/Sdk"
    
    if [ -n "$JAVA_HOME" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    if [ -n "$ANDROID_HOME" ]; then
        export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
    fi
fi

echo "--- Starting build and publish of $APP_NAME ---"

# 1. Build
echo "--- Building $APP_NAME (Release APK) ---"

# Ensure keystore exists
if [ ! -f "keystore.jks" ] && [ -z "$SIGNING_STORE_FILE" ]; then
    echo "Note: keystore.jks not found, generating a development keystore..."
    ./gradlew generateKeystore
fi

# Set default signing environment variables if not already set
# This ensures the build succeeds even if local.properties is missing signing info
export SIGNING_STORE_FILE="${SIGNING_STORE_FILE:-$PWD/keystore.jks}"
export SIGNING_STORE_PASSWORD="${SIGNING_STORE_PASSWORD:-changeit}"
export SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS:-upload}"
export SIGNING_KEY_PASSWORD="${SIGNING_KEY_PASSWORD:-$SIGNING_STORE_PASSWORD}"

./gradlew clean :$MODULE:assembleRelease

# 2. Extract Version Info from build.gradle
echo "--- Extracting version info ---"
VERSION_NAME=$(grep "versionName" $MODULE/build.gradle | head -n 1 | sed 's/.*"\(.*\)".*/\1/' || echo "unknown")
VERSION_CODE=$(grep "versionCode" $MODULE/build.gradle | head -n 1 | sed 's/[^0-9]*//g' || echo "0")

# 3. Publish
echo "--- Publishing Artifacts to $DIST_DIR ---"
mkdir -p "$DIST_DIR"

APK_PATH="$MODULE/build/outputs/apk/release/$MODULE-release.apk"
UNSIGNED_APK_PATH="$MODULE/build/outputs/apk/release/$MODULE-release-unsigned.apk"

if [ -f "$APK_PATH" ]; then
    DEST="$DIST_DIR/$APP_NAME-v$VERSION_NAME-$VERSION_CODE.apk"
    cp "$APK_PATH" "$DEST"
    echo "Successfully published: $DEST"
elif [ -f "$UNSIGNED_APK_PATH" ]; then
    DEST="$DIST_DIR/$APP_NAME-v$VERSION_NAME-$VERSION_CODE-unsigned.apk"
    cp "$UNSIGNED_APK_PATH" "$DEST"
    echo "Successfully published (UNSIGNED): $DEST"
    echo "Note: Configure signing in local.properties to produce signed release builds."
else
    echo "ERROR: Could not find resulting APK artifact."
    exit 1
fi

echo "--- All builds and publishes completed ---"
