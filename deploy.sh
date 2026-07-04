#!/bin/bash
set -e

# Configuration
APP_NAME="ultrasonic"
DIST_DIR="dist"
DOCKER_IMAGE="tsshadow/apk-hoster"

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

echo "--- Starting deployment of $APP_NAME ---"

# 1. Remote Publish (Optional)
if [ -n "$PUBLISH_REMOTE_PATH" ]; then
    echo "--- Syncing to remote server: $PUBLISH_REMOTE_PATH ---"
    scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
fi

# 2. Remote Docker Restart (Optional)
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
fi

echo "--- Deployment completed ---"
