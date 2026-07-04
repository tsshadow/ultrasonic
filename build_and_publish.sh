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

# Load optional remote publish path from local.properties if exists
if [ -f "local.properties" ]; then
    PROP_PATH=$(grep "^PUBLISH_REMOTE_PATH=" local.properties | cut -d'=' -f2-)
    if [ -n "$PROP_PATH" ]; then
        PUBLISH_REMOTE_PATH="${PUBLISH_REMOTE_PATH:-$PROP_PATH}"
    fi
    # Remote host configuration for Docker deployment
    REMOTE_HOST="${REMOTE_HOST:-$(grep "^REMOTE_HOST=" local.properties | cut -d'=' -f2-)}"
    REMOTE_USER="${REMOTE_USER:-$(grep "^REMOTE_USER=" local.properties | cut -d'=' -f2-)}"
    REMOTE_PASS="${REMOTE_PASS:-$(grep "^REMOTE_PASS=" local.properties | cut -d'=' -f2-)}"
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

# 4. Generate Index HTML for easy distribution
echo "--- Updating index.html ---"
cat > "$DIST_DIR/index.html" <<EOF
<!DOCTYPE html>
<html>
<head>
    <title>$APP_NAME Downloads</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; padding: 20px; max-width: 800px; margin: 0 auto; background: #f4f7f9; color: #333; }
        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; margin-bottom: 30px; }
        .apk-list { list-style: none; padding: 0; }
        .apk-item { margin-bottom: 15px; padding: 20px; border-radius: 12px; background: white; box-shadow: 0 4px 6px rgba(0,0,0,0.05); transition: transform 0.2s; border-left: 5px solid #3498db; }
        .apk-item:hover { transform: translateY(-2px); box-shadow: 0 6px 12px rgba(0,0,0,0.1); }
        .apk-link { font-weight: bold; font-size: 1.1em; text-decoration: none; color: #2980b9; display: block; word-break: break-all; }
        .apk-info { color: #7f8c8d; font-size: 0.85em; margin-top: 8px; display: flex; justify-content: space-between; }
        .tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.8em; font-weight: bold; background: #e0e0e0; margin-left: 10px; }
        .tag-latest { background: #27ae60; color: white; }
    </style>
</head>
<body>
    <h1>$APP_NAME Downloads</h1>
    <ul class="apk-list">
EOF

# Use ls -t to sort by time, and loop through files
IS_FIRST=true
# Use a temporary file to avoid globbing issues in for loop if filenames have spaces
ls -t "$DIST_DIR"/*.apk > /tmp/apk_list.txt
while IFS= read -r apk; do
    [ -z "$apk" ] && continue
    filename=$(basename "$apk")
    filedate=$(date -r "$apk" "+%Y-%m-%d %H:%M")
    
    LATEST_TAG=""
    if [ "$IS_FIRST" = true ]; then
        LATEST_TAG="<span class='tag tag-latest'>LATEST</span>"
        IS_FIRST=false
    fi
    
    cat >> "$DIST_DIR/index.html" <<EOF
        <li class="apk-item">
            <a class="apk-link" href="$filename">$filename $LATEST_TAG</a>
            <div class="apk-info">
                <span>Built: $filedate</span>
                <span class="tag">APK</span>
            </div>
        </li>
EOF
done < /tmp/apk_list.txt

cat >> "$DIST_DIR/index.html" <<EOF
    </ul>
    <footer style="margin-top: 40px; text-align: center; color: #95a5a6; font-size: 0.8em;">
        <p>Generated by build_and_publish.sh on $(date "+%Y-%m-%d %H:%M:%S")</p>
    </footer>
</body>
</html>
EOF

# 5. Remote Publish (Optional)
if [ -n "$PUBLISH_REMOTE_PATH" ]; then
    echo "--- Syncing to remote server: $PUBLISH_REMOTE_PATH ---"
    scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
fi

# 6. Docker Publish (Optional)
DOCKER_IMAGE="tsshadow/apk-hoster"
if command -v docker >/dev/null 2>&1; then
    echo "--- Building Docker Image: $DOCKER_IMAGE ---"
    # Ensure we have the latest main.go and Dockerfile in the context
    docker build -t "$DOCKER_IMAGE" -f apk-hoster/Dockerfile .
    
    echo "--- Pushing Docker Image: $DOCKER_IMAGE ---"
    docker push "$DOCKER_IMAGE"
    
    if [ -n "$REMOTE_HOST" ]; then
        echo "--- Updating remote server $REMOTE_HOST ---"
        REMOTE_USER="${REMOTE_USER:-root}"
        
        REMOTE_COMMAND="
        set -e
        docker pull $DOCKER_IMAGE
        docker stop apk-hoster || true
        docker rm apk-hoster || true
        docker run -d --name apk-hoster \
            -p 8275:8275 \
            --restart always \
            $DOCKER_IMAGE
        "
        
        if [ -z "${REMOTE_PASS}" ]; then
            ssh -o StrictHostKeyChecking=no "${REMOTE_USER}@${REMOTE_HOST}" "${REMOTE_COMMAND}"
        elif command -v sshpass >/dev/null 2>&1; then
            sshpass -p "${REMOTE_PASS}" ssh -o StrictHostKeyChecking=no "${REMOTE_USER}@${REMOTE_HOST}" "${REMOTE_COMMAND}"
        else
            echo "Error: sshpass not found. Install it or unset REMOTE_PASS and enter password manually."
        fi
    else
        echo "Note: REMOTE_HOST not set, skipping remote deployment."
        echo "To restart the container manually, run:"
        echo "docker pull $DOCKER_IMAGE && docker stop apk-hoster || true && docker rm apk-hoster || true && docker run -d --name apk-hoster -p 8275:8275 --restart always $DOCKER_IMAGE"
    fi
else
    echo "Warning: docker command not found, skipping Docker build."
fi

echo "--- All builds and publishes completed ---"
echo ""
echo "Quick Distribute: Run the following to host your APKs on your local network:"
echo "cd $DIST_DIR && python3 -m http.server 8080"
echo "Then visit http://$(hostname -I | awk '{print $1}'):8080 on your phone."
