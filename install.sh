#!/bin/bash
set -e

# Configuration
JDK_VERSION="21.0.6+7"
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"
SDK_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
INSTALL_DIR="$HOME/.local"
JDK_DIR="$INSTALL_DIR/jdk-21"
ANDROID_HOME="$HOME/Android/Sdk"

echo "=== Ultrasonic Dependency Installer ==="

mkdir -p "$INSTALL_DIR"

# 1. Install System Dependencies
if ! command -v sshpass >/dev/null 2>&1; then
    echo "--- Installing sshpass ---"
    sudo apt-get update -y && sudo apt-get install -y sshpass
fi

# Add current user to docker group
if ! groups $USER | grep -q "\bdocker\b"; then
    echo "Adding $USER to docker group..."
    sudo usermod -aG docker $USER
    echo "User added to docker group. You may need to restart your session or run 'newgrp docker'."
fi

# 2. Install JDK 21
if [ ! -d "$JDK_DIR" ] || [ ! -f "$JDK_DIR/bin/java" ]; then
    echo "--- Installing JDK 21 ---"
    wget -q "$JDK_URL" -O jdk.tar.gz
    mkdir -p "$JDK_DIR"
    tar -xzf jdk.tar.gz -C "$JDK_DIR" --strip-components=1
    rm jdk.tar.gz
    echo "JDK 21 installed at $JDK_DIR"
else
    echo "JDK 21 already installed at $JDK_DIR"
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Setup Android SDK
mkdir -p "$ANDROID_HOME"
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "--- Installing Android Command Line Tools ---"
    wget -q "$SDK_TOOLS_URL" -O sdk-tools.zip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    # Unzip into a temporary folder to handle the nested structure
    unzip -q sdk-tools.zip -d "$ANDROID_HOME/cmdline-tools/temp"
    mv "$ANDROID_HOME/cmdline-tools/temp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$ANDROID_HOME/cmdline-tools/temp"
    rm sdk-tools.zip
    echo "Android Command Line Tools installed"
else
    echo "Android Command Line Tools already installed"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# 3. Install SDK Components
echo "--- Installing Android SDK components ---"
# Accept licenses
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > /dev/null

sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 4. Setup local.properties
echo "--- Configuring project ---"
cat > local.properties <<EOF
sdk.dir=$ANDROID_HOME
SIGNING_STORE_FILE=$(pwd)/keystore.jks
SIGNING_STORE_PASSWORD=changeit
SIGNING_KEY_ALIAS=upload
SIGNING_KEY_PASSWORD=changeit
# Optional: Remote publish path for build_and_publish.sh
# PUBLISH_REMOTE_PATH=user@nas:/var/www/html/ultrasonic

# Remote Deployment Configuration
# Descriptive name for the deployment target
# DEPLOY_TARGET_NAME=APK Hoster

# Option 1: Portainer Webhook
# PORTAINER_WEBHOOK_URL=https://portainer.example.com/api/webhooks/...

# Option 2: SSH-based deployment
# REMOTE_HOST=192.168.1.27
# REMOTE_USER=root
# REMOTE_PASS=changeit
# REMOTE_DIST_PATH=/var/www/html/ultrasonic

# Default Server Configurations for APK pre-configuration
# SERVER_1_NAME=LMS
# SERVER_1_URL=https://lms.teunschriks.nl
# SERVER_1_USER=guest
# SERVER_1_PASS=your_uuid_here
# SERVER_2_NAME=Spotify
# SERVER_2_URL=https://spotify.teunschriks.nl
# SERVER_2_USER=guest
# SERVER_2_PASS=your_uuid_here
EOF
echo "local.properties updated"

# 5. Environment check
echo "--- Verification ---"
java -version
sdkmanager --version

# Apply group membership to the current session
if ! groups | grep -q "\bdocker\b"; then
    echo "Applying docker group membership to the current session via 'newgrp docker'..."
    newgrp docker
fi
echo ""
echo "=== Installation Successful ==="
echo "You can now run: ./gradlew assembleDebug"
echo "Or build a release APK with: ./build_and_publish.sh"
echo ""
echo "To make these changes permanent, add the following to your ~/.bashrc:"
echo "export JAVA_HOME=\"$JDK_DIR\""
echo "export ANDROID_HOME=\"$ANDROID_HOME\""
echo "export PATH=\"\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
