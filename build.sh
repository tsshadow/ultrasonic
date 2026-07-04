#!/bin/bash
set -e

# Configuration
APP_NAME="ultrasonic"
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

echo "--- Starting build of $APP_NAME ---"

# Ensure keystore exists
if [ ! -f "keystore.jks" ] && [ -z "$SIGNING_STORE_FILE" ]; then
    echo "Note: keystore.jks not found, generating a development keystore..."
    ./gradlew generateKeystore
fi

# Set default signing environment variables if not already set
export SIGNING_STORE_FILE="${SIGNING_STORE_FILE:-$PWD/keystore.jks}"
export SIGNING_STORE_PASSWORD="${SIGNING_STORE_PASSWORD:-changeit}"
export SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS:-upload}"
export SIGNING_KEY_PASSWORD="${SIGNING_KEY_PASSWORD:-$SIGNING_STORE_PASSWORD}"

./gradlew clean :$MODULE:assembleRelease

echo "--- Build completed successfully ---"
