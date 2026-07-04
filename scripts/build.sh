#!/bin/bash
set -e

# Configuration
APP_NAME="ultrasonic"
MODULE="ultrasonic"

cd "$(dirname "$0")/.."
ROOT_DIR=$(pwd)

# Try to find JAVA_HOME and ANDROID_HOME if not set
if [ -z "$JAVA_HOME" ] || [ -z "$ANDROID_HOME" ]; then
    echo "Note: Environment variables not set, attempting to load defaults..."
    [ -d "$HOME/.local/jdk-21" ] && export JAVA_HOME="$HOME/.local/jdk-21"
    [ -d "$HOME/Android/Sdk" ] && export ANDROID_HOME="$HOME/Android/Sdk"
    
    if [ -n "$JAVA_HOME" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    if [ -n "$ANDROID_HOME" ]; then
        export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
    fi
fi

# Build type (release or debug)
BUILD_TYPE=$(echo "${1:-debug}" | tr '[:upper:]' '[:lower:]')
if [[ "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "debug" ]]; then
    echo "Invalid build type: $BUILD_TYPE. Use 'release' or 'debug'."
    exit 1
fi

echo "--- Starting $BUILD_TYPE build of $APP_NAME ---"

# Ensure keystore exists
if [ ! -f "keystore.jks" ] && [ -z "$SIGNING_STORE_FILE" ]; then
    echo "Note: keystore.jks not found, generating a development keystore..."
    ./gradlew generateKeystore
fi

# Load optional configuration from .env
if [ -f ".env" ]; then
    while IFS='=' read -r key value; do
        if [[ ! $key =~ ^# && -n $key ]]; then
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            # Only export valid bash identifiers (skip keys with dots like sdk.dir)
            if [[ "$key" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
                export "$key=$value"
            fi
        fi
    done < .env
fi

# Set signing environment variables from config
export SIGNING_STORE_FILE="${SIGNING_STORE_FILE}"
export SIGNING_STORE_PASSWORD="${SIGNING_STORE_PASSWORD}"
export SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS}"
export SIGNING_KEY_PASSWORD="${SIGNING_KEY_PASSWORD}"

GRADLE_TASK="assembleRelease"
if [ "$BUILD_TYPE" == "debug" ]; then
    GRADLE_TASK="assembleDebug"
fi

./gradlew clean :$MODULE:$GRADLE_TASK

echo "--- $BUILD_TYPE Build completed successfully ---"

echo "--- Build completed successfully ---"
