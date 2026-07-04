#!/bin/bash
set -e

cd "$(dirname "$0")"

# Configuration
APP_NAME="ultrasonic"
DIST_DIR="dist"
DOCKER_IMAGE="tsshadow/apk-hoster"

# Load optional remote publish path from local.properties if exists
if [ -f "local.properties" ]; then
    # Parse local.properties
    while IFS='=' read -r key value; do
        if [[ ! $key =~ ^# && -n $key ]]; then
            # Trim whitespace
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            export "$key=$value"
        fi
    done < local.properties
    
    PUBLISH_REMOTE_PATH="${PUBLISH_REMOTE_PATH}"
fi

# Configuration for deployment logic
TARGET="${DEPLOY_TARGET_NAME:-APK Hoster Stack}"
DOCKER_COMPOSE_FILE="${REMOTE_STACK_PATH:-docker-compose.yml}"

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
    if [ -z "${REMOTE_PASS}" ]; then
        scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    elif command -v sshpass >/dev/null 2>&1; then
        sshpass -p "${REMOTE_PASS}" scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    else
        scp "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    fi
fi

# 2. Remote Deployment (Optional)
if [ -n "${PORTAINER_WEBHOOK_URL}" ]; then
    echo "--- Triggering Portainer Webhook for: $TARGET ---"
    if command -v curl >/dev/null 2>&1; then
        curl -X POST "${PORTAINER_WEBHOOK_URL}"
        echo -e "\n--- Webhook triggered for $TARGET ---"
    else
        echo "Error: curl not found. Cannot trigger Portainer Webhook."
        exit 1
    fi
elif [ -n "$REMOTE_HOST" ]; then
    echo "--- Updating remote server $REMOTE_HOST for: $TARGET ---"
    REMOTE_USER="${REMOTE_USER:-root}"
    
    # Prepare volume mount if remote path is known
    VOLUME_ARG=""
    if [ -n "$REMOTE_DIST_PATH" ]; then
        VOLUME_ARG="-v \"$REMOTE_DIST_PATH:/app/dist\""
    fi

    REMOTE_COMMAND="
    set -e
    # Try to find docker-compose file
    if [ -f \"${DOCKER_COMPOSE_FILE}\" ]; then
        echo \"Updating stack '$TARGET' via docker-compose using ${DOCKER_COMPOSE_FILE}...\"
        docker-compose -f \"${DOCKER_COMPOSE_FILE}\" pull || docker compose -f \"${DOCKER_COMPOSE_FILE}\" pull
        docker-compose -f \"${DOCKER_COMPOSE_FILE}\" up -d || docker compose -f \"${DOCKER_COMPOSE_FILE}\" up -d
    elif [ -d \"${DOCKER_COMPOSE_FILE}\" ] && [ -f \"${DOCKER_COMPOSE_FILE}/docker-compose.yml\" ]; then
        echo \"Updating stack '$TARGET' in directory ${DOCKER_COMPOSE_FILE}...\"
        cd \"${DOCKER_COMPOSE_FILE}\"
        docker-compose pull || docker compose pull
        docker-compose up -d || docker compose up -d
    else
        echo \"Warning: Docker compose configuration not found at '${DOCKER_COMPOSE_FILE}' on remote host.\"
        echo \"Updating individual container 'apk-hoster' for '$TARGET'...\"
        docker pull $DOCKER_IMAGE
        docker stop apk-hoster || true
        docker rm apk-hoster || true
        docker run -d --name apk-hoster \
            -p 8275:8275 \
            $VOLUME_ARG \
            --restart always \
            $DOCKER_IMAGE
    fi
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
