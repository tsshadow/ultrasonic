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

# 1. Install JDK 21
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
# Optional: Remote Docker host for build_and_publish.sh
# REMOTE_HOST=192.168.1.27
# REMOTE_USER=root
# REMOTE_PASS=changeit
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
