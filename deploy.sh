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
    REMOTE_DIST_PATH="${REMOTE_DIST_PATH:-$(grep "^REMOTE_DIST_PATH=" local.properties | cut -d'=' -f2-)}"
    PORTAINER_WEBHOOK_URL="${PORTAINER_WEBHOOK_URL:-$(grep "^PORTAINER_WEBHOOK_URL=" local.properties | cut -d'=' -f2-)}"
    DEPLOY_TARGET_NAME="${DEPLOY_TARGET_NAME:-$(grep "^DEPLOY_TARGET_NAME=" local.properties | cut -d'=' -f2-)}"
fi

# Try to extract path from PUBLISH_REMOTE_PATH if REMOTE_DIST_PATH is not set
if [ -z "$REMOTE_DIST_PATH" ] && [ -n "$PUBLISH_REMOTE_PATH" ]; then
    if [[ "$PUBLISH_REMOTE_PATH" == *":"* ]]; then
        REMOTE_DIST_PATH=$(echo "$PUBLISH_REMOTE_PATH" | cut -d':' -f2-)
    else
        REMOTE_DIST_PATH="$PUBLISH_REMOTE_PATH"
    fi
fi

echo "--- Starting deployment of $APP_NAME ---"

# 1. Remote Publish (Optional)
if [ -n "$PUBLISH_REMOTE_PATH" ]; then
    echo "--- Syncing to remote server: $PUBLISH_REMOTE_PATH ---"
    scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
fi

# 2. Remote Deployment (Optional)
if [ -n "${PORTAINER_WEBHOOK_URL}" ]; then
    TARGET="${DEPLOY_TARGET_NAME:-Portainer Stack}"
    echo "--- Triggering Portainer Webhook for: $TARGET ---"
    if command -v curl >/dev/null 2>&1; then
        curl -X POST "${PORTAINER_WEBHOOK_URL}"
        echo -e "\n--- Webhook triggered for $TARGET ---"
    else
        echo "Error: curl not found. Cannot trigger Portainer Webhook."
        exit 1
    fi
elif [ -n "$REMOTE_HOST" ]; then
    TARGET="${DEPLOY_TARGET_NAME:-APK Hoster}"
    echo "--- Updating remote server $REMOTE_HOST for: $TARGET ---"
    REMOTE_USER="${REMOTE_USER:-root}"
    
    # Prepare volume mount if remote path is known
    VOLUME_ARG=""
    if [ -n "$REMOTE_DIST_PATH" ]; then
        VOLUME_ARG="-v \"$REMOTE_DIST_PATH:/app/dist\""
    fi

    REMOTE_COMMAND="
    set -e
    echo \"Updating container 'apk-hoster' for '$TARGET'...\"
    docker pull $DOCKER_IMAGE
    docker stop apk-hoster || true
    docker rm apk-hoster || true
    docker run -d --name apk-hoster \
        -p 8275:8275 \
        $VOLUME_ARG \
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
    echo "Note: Neither PORTAINER_WEBHOOK_URL nor REMOTE_HOST set, skipping remote deployment."
fi

echo "--- Deployment completed ---"
