#!/bin/bash
set -e

# MuMaFi Ultrasonic Install Script
# Standardized interface for building, publishing, and deploying.

# Get the project root
ROOT_DIR="$(dirname "$(readlink -f "$0")")"
cd "$ROOT_DIR"

# Application-specific configuration
PROJECT_NAME="Ultrasonic"
AVAILABLE_APPS=("ultrasonic")
DEFAULT_APP="ultrasonic"

show_help() {
    echo "MuMaFi $PROJECT_NAME Install Script"
    echo "Usage: ./install.sh [options] [mode]"
    echo ""
    echo "Options:"
    echo "  --help          Show this help message"
    echo "  --app=<app>     Specify the application to install (default: $DEFAULT_APP)"
    echo "  --list          List available applications"
    echo ""
    echo "Modes:"
    echo "  debug           Build in debug mode (default)"
    echo "  release         Build in release mode"
    echo "  install         Build and install on connected device (debug)"
    echo "  patch           Increment patch version and build release"
    echo "  minor           Increment minor version and build release"
    echo "  major           Increment major version and build release"
    echo ""
    echo "Examples:"
    echo "  ./install.sh install"
    echo "  ./install.sh patch"
}

list_apps() {
    for app in "${AVAILABLE_APPS[@]}"; do
        echo "$app"
    done
}

# Default values
MODE=""

# Parse arguments
for i in "$@"; do
    case $i in
        --help)
            show_help
            exit 0
            ;;
        --list)
            list_apps
            exit 0
            ;;
        --app=*)
            DEFAULT_APP="${i#*=}"
            # Validate app
            if [[ ! " ${AVAILABLE_APPS[@]} " =~ " ${DEFAULT_APP} " ]]; then
                echo "Error: Application '$DEFAULT_APP' not found."
                echo "Available: ${AVAILABLE_APPS[*]}"
                exit 1
            fi
            ;;
        debug|release|patch|minor|major|install)
            MODE="$i"
            ;;
    esac
done

if [ -z "$MODE" ]; then
    MODE="debug"
fi

if [ "$MODE" == "install" ]; then
    echo "--- Building and Installing $PROJECT_NAME on device ---"
    
    # Try to find JAVA_HOME and ANDROID_HOME if not set (mirroring scripts/build.sh)
    if [ -z "$JAVA_HOME" ] || [ -z "$ANDROID_HOME" ]; then
        [ -d "$HOME/.local/jdk-21" ] && export JAVA_HOME="$HOME/.local/jdk-21"
        [ -d "$HOME/Android/Sdk" ] && export ANDROID_HOME="$HOME/Android/Sdk"
        
        if [ -n "$JAVA_HOME" ]; then
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
        if [ -n "$ANDROID_HOME" ]; then
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
        fi
    fi
    
    ./gradlew :$DEFAULT_APP:installDebug
else
    ./scripts/build-and-publish.sh "$MODE"
fi
