#!/bin/bash
set -e

cd "$(dirname "$0")/.."
ROOT_DIR=$(pwd)

# Configuration
APP_NAME="ultrasonic"

# Load optional remote publish path from .env if exists
if [ -f ".env" ]; then
    # Parse .env
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
    done < .env
    
    PUBLISH_REMOTE_PATH="${PUBLISH_REMOTE_PATH}"
fi

# Build type (release or debug) - only relevant for deployment if we filter what to sync
BUILD_TYPE=$(echo "${1:-debug}" | tr '[:upper:]' '[:lower:]')

# Configuration for deployment logic
DIST_DIR="${DIST_DIR:-dist}"
TARGET="${DEPLOY_TARGET_NAME:-Ultrasonic}"
REMOTE_DIST_PATH="${REMOTE_DIST_PATH:-/mnt/teun/ultrasonic-builds}"

# Try to construct PUBLISH_REMOTE_PATH if not set but we have remote info
if [ -z "$PUBLISH_REMOTE_PATH" ] && [ -n "$REMOTE_HOST" ] && [ -n "$REMOTE_DIST_PATH" ]; then
    USER_PART="${REMOTE_USER:-root}"
    PUBLISH_REMOTE_PATH="${USER_PART}@${REMOTE_HOST}:${REMOTE_DIST_PATH}"
    echo "Note: PUBLISH_REMOTE_PATH not set, inferred: $PUBLISH_REMOTE_PATH"
fi

echo "--- Starting $BUILD_TYPE deployment of $APP_NAME ---"

# 1. Remote Publish (Optional)
if [ -n "$PUBLISH_REMOTE_PATH" ]; then
    echo "--- Syncing to remote server: $PUBLISH_REMOTE_PATH ---"
    
    # Create the remote directory if it doesn't exist
    REMOTE_HOST_ONLY=$(echo "$PUBLISH_REMOTE_PATH" | cut -d':' -f1)
    REMOTE_PATH_ONLY=$(echo "$PUBLISH_REMOTE_PATH" | cut -d':' -f2-)
    
    echo "Ensuring remote directory exists: $REMOTE_PATH_ONLY"
    if [ -z "${REMOTE_PASS}" ]; then
        ssh -o StrictHostKeyChecking=no "$REMOTE_HOST_ONLY" "mkdir -p $REMOTE_PATH_ONLY"
        scp -o StrictHostKeyChecking=no "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    elif command -v sshpass >/dev/null 2>&1; then
        sshpass -p "${REMOTE_PASS}" ssh -o StrictHostKeyChecking=no "$REMOTE_HOST_ONLY" "mkdir -p $REMOTE_PATH_ONLY"
        sshpass -p "${REMOTE_PASS}" scp -o StrictHostKeyChecking=no "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    else
        ssh -o StrictHostKeyChecking=no "$REMOTE_HOST_ONLY" "mkdir -p $REMOTE_PATH_ONLY"
        scp -o StrictHostKeyChecking=no "$DIST_DIR/"* "$PUBLISH_REMOTE_PATH/"
    fi
fi

# 2. Remote Notification/Webhook (Optional)
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
    echo "--- Remote file sync completed ---"
else
    echo "Note: Neither PORTAINER_WEBHOOK_URL nor REMOTE_HOST set, skipping remote publish."
fi

echo "--- Deployment completed ---"
