#!/bin/bash
set -e

cd "$(dirname "$0")"

# Configuration
APP_NAME="ultrasonic"

# Load optional remote publish path from local.properties if exists
if [ -f "local.properties" ]; then
    # Parse local.properties
    while IFS='=' read -r key value; do
        if [[ ! $key =~ ^# && -n $key ]]; then
            # Trim whitespace
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            # Only export valid bash identifiers (skip keys with dots like sdk.dir)
            if [[ "$key" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
                export "$key=$value"
            fi
        fi
    done < local.properties
    
    PUBLISH_REMOTE_PATH="${PUBLISH_REMOTE_PATH}"
fi

# Configuration for deployment logic
DIST_DIR="${DIST_DIR:-dist}"
TARGET="${DEPLOY_TARGET_NAME:-Ultrasonic}"
DOCKER_COMPOSE_FILE="${REMOTE_STACK_PATH}"
DOCKER_IMAGE="${DOCKER_IMAGE:-tsshadow/apk-hoster}"
REMOTE_DIST_PATH="${REMOTE_DIST_PATH:-/mnt/teun/ultrasonic-builds}"

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
        STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${PORTAINER_WEBHOOK_URL}")
        if [ "$STATUS_CODE" -ge 200 ] && [ "$STATUS_CODE" -lt 300 ]; then
            echo "--- Webhook triggered successfully for $TARGET (Status: $STATUS_CODE) ---"
        elif [ "$STATUS_CODE" -eq 404 ]; then
            echo "Error: Portainer Webhook returned 404 Not Found."
            echo "Note: Stack Webhooks are a Business Edition feature."
            if [[ "${PORTAINER_WEBHOOK_URL}" == *"/stacks/"* ]]; then
                echo "HINT: You are using a Stack Webhook URL, which requires Portainer Business Edition."
                echo "If you have Community Edition, you can use 'Service Webhooks' (under each service) or the SSH method."
            fi
            if [[ "${PORTAINER_WEBHOOK_URL}" == *"ptr_"* ]]; then
                echo "HINT: Your URL seems to contain an API Access Token (ptr_...). Webhooks use a different token."
            fi
            exit 1
        else
            echo "Error: Portainer Webhook failed with status code $STATUS_CODE"
            exit 1
        fi
    else
        echo "Error: curl not found. Cannot trigger Portainer Webhook."
        exit 1
    fi
elif [ -n "$REMOTE_HOST" ]; then
    echo "--- Starting remote deployment for stack: $TARGET ---"
    
    # Create temporary .env from local.properties for deployment if it exists
    TEMP_ENV=$(mktemp)
    # Start with defaults for essential variables
    echo "DOCKER_IMAGE=${DOCKER_IMAGE}" > "$TEMP_ENV"
    echo "REMOTE_DIST_PATH=${REMOTE_DIST_PATH}" >> "$TEMP_ENV"
    
    if [ -f "local.properties" ]; then
        grep -v '^#' local.properties | sed 's/ *= */=/g' >> "$TEMP_ENV"
    fi
    export LOCAL_ENV_FILE="$TEMP_ENV"
    
    # Use the generalized deployment script
    export SEARCH_STRING="apk-hoster"
    export LOCAL_COMPOSE_FILE="docker-compose.yml"
    
    ./scripts/deploy-stack.sh
    
    rm -f "$TEMP_ENV"
    echo "--- Deployment completed successfully ---"
else
    echo "Note: Neither PORTAINER_WEBHOOK_URL nor REMOTE_HOST set, skipping remote deployment."
fi

echo "--- Deployment completed ---"
