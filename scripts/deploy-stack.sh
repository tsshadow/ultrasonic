#!/bin/bash
# Generalized Docker Stack Deployment Script
# Supports: Portainer discovery, SSH transfer, Service-specific updates, and Initial creation.

set -e

# Helper function to run SSH command
run_ssh() {
    local host="$1"
    local user="$2"
    local pass="$3"
    local cmd="$4"
    
    if [ -z "$host" ] || [ -z "$user" ]; then
        return 1
    fi

    if [ -z "$pass" ]; then
        ssh -o StrictHostKeyChecking=no "${user}@${host}" "${cmd}"
    elif command -v sshpass >/dev/null 2>&1; then
        sshpass -p "${pass}" ssh -o StrictHostKeyChecking=no "${user}@${host}" "${cmd}"
    else
        echo "Error: sshpass not found. Install it or unset password variable."
        exit 1
    fi
}

# Parameters (usually from environment)
STACK_NAME="${DEPLOY_TARGET_NAME:-$1}"
STACK_NAME=$(echo "$STACK_NAME" | tr '[:upper:]' '[:lower:]')
SERVICE_NAME="${SERVICE_NAME:-$2}"
IMAGE_NAME="${IMAGE_NAME:-$3}"
TAG="${TAG:-$4}"
SEARCH_STRING="${SEARCH_STRING:-$IMAGE_NAME}"
LOCAL_ENV_FILE="${LOCAL_ENV_FILE:-$5}"

# Discovery command to be run on remote hosts
DISCOVERY_CMD="
    ACTUAL_COMPOSE_FILE=\"\"
    # 1. Try to find via running container labels for the target project
    if [ -n \"$STACK_NAME\" ]; then
        CONTAINER_ID=\$(docker ps -q --filter \"label=com.docker.compose.project=$STACK_NAME\" | head -n 1)
        if [ -n \"\$CONTAINER_ID\" ]; then
            ACTUAL_COMPOSE_FILE=\$(docker inspect \"\$CONTAINER_ID\" --format '{{ index .Config.Labels \"com.docker.compose.project.config_files\" }}' 2>/dev/null | cut -d, -f1)
        fi
    fi
    # 2. Try to find via SERVICE_NAME label
    if ([ -z \"\$ACTUAL_COMPOSE_FILE\" ] || [ ! -f \"\$ACTUAL_COMPOSE_FILE\" ]) && [ -n \"$SERVICE_NAME\" ]; then
        ACTUAL_COMPOSE_FILE=\$(docker inspect \"$SERVICE_NAME\" --format '{{ index .Config.Labels \"com.docker.compose.project.config_files\" }}' 2>/dev/null | cut -d, -f1)
    fi
    # 3. Fallback to provided path
    if ([ -z \"\$ACTUAL_COMPOSE_FILE\" ] || [ ! -f \"\$ACTUAL_COMPOSE_FILE\" ]) && [ -n \"$REMOTE_STACK_PATH\" ]; then
        if [ -f \"\$REMOTE_STACK_PATH\" ]; then ACTUAL_COMPOSE_FILE=\"\$REMOTE_STACK_PATH\"; fi
    fi
    # 4. Search common paths
    if [ -z \"\$ACTUAL_COMPOSE_FILE\" ] || [ ! -f \"\$ACTUAL_COMPOSE_FILE\" ]; then
        for SEARCH_DIR in /data/compose /var/lib/docker/volumes/*/ _data/compose /var/lib/docker/volumes/portainer_portainer_data/_data/compose; do
            if [ -d \"\$SEARCH_DIR\" ]; then
                MATCH=\$(grep -rl \"$SEARCH_STRING\" \"\$SEARCH_DIR\" 2>/dev/null | grep \"docker-compose.yml\" | head -n 1)
                if [ -f \"\$MATCH\" ]; then ACTUAL_COMPOSE_FILE=\"\$MATCH\"; break; fi
            fi
        done
    fi
    if [ -n \"\$ACTUAL_COMPOSE_FILE\" ] && [ -f \"\$ACTUAL_COMPOSE_FILE\" ]; then
        echo \"FOUND_FILE:\$ACTUAL_COMPOSE_FILE\"
        cat \"\$ACTUAL_COMPOSE_FILE\"
    fi
"

COMPOSE_CONTENT=""
SOURCE_HOST=""

# 1. Use local compose file if provided (Source of truth for deployment)
if [ -n "$LOCAL_COMPOSE_FILE" ] && [ -f "$LOCAL_COMPOSE_FILE" ]; then
    echo "Using local configuration file: $LOCAL_COMPOSE_FILE"
    COMPOSE_CONTENT=$(expand -t 4 "$LOCAL_COMPOSE_FILE")
    SOURCE_HOST="local"
fi

# 2. Try discovery on PORTAINER_HOST if local not provided
if [ -z "$COMPOSE_CONTENT" ] && [ -n "$PORTAINER_HOST" ]; then
    echo "Checking Portainer host $PORTAINER_HOST for stack template..."
    P_USER="${PORTAINER_HOST_USER:-$REMOTE_USER}"
    P_PASS="${PORTAINER_HOST_PASS:-$REMOTE_PASS}"
    RESULT=$(run_ssh "$PORTAINER_HOST" "$P_USER" "$P_PASS" "REMOTE_STACK_PATH='$REMOTE_STACK_PATH' $DISCOVERY_CMD" 2>/dev/null || true)
    if echo "$RESULT" | grep -q "^FOUND_FILE:"; then
        COMPOSE_CONTENT=$(echo "$RESULT" | sed -n '/^FOUND_FILE:/!p' | expand -t 4)
        SOURCE_HOST="$PORTAINER_HOST"
        echo "Found template on $PORTAINER_HOST"
    fi
fi

# 3. Try discovery on REMOTE_HOST if still not found
if [ -z "$COMPOSE_CONTENT" ] && [ -n "$REMOTE_HOST" ]; then
    echo "Checking remote host $REMOTE_HOST for existing configuration..."
    RESULT=$(run_ssh "$REMOTE_HOST" "$REMOTE_USER" "$REMOTE_PASS" "REMOTE_STACK_PATH='$REMOTE_STACK_PATH' $DISCOVERY_CMD" 2>/dev/null || true)
    if echo "$RESULT" | grep -q "^FOUND_FILE:"; then
        COMPOSE_CONTENT=$(echo "$RESULT" | sed -n '/^FOUND_FILE:/!p' | expand -t 4)
        SOURCE_HOST="$REMOTE_HOST"
        echo "Found existing configuration on $REMOTE_HOST"
    fi
fi

if [ -z "$COMPOSE_CONTENT" ]; then
    echo "Error: No docker-compose configuration found (tried Portainer, Remote, and Local)."
    exit 1
fi

# Final deployment logic to be run on REMOTE_HOST
DEPLOY_CMD="
    set -e
    FINAL_COMPOSE_FILE=\"/tmp/docker-compose.$STACK_NAME.yml\"
    echo \"\$BASE64_COMPOSE\" | base64 -d > \"\$FINAL_COMPOSE_FILE\"
    
    # Handle environment variables
    ENV_ARG=\"\"
    if [ -n \"\$BASE64_ENV\" ]; then
        FINAL_ENV_FILE=\"/tmp/.env.$STACK_NAME\"
        echo \"\$BASE64_ENV\" | base64 -d > \"\$FINAL_ENV_FILE\"
        ENV_ARG=\"--env-file \$FINAL_ENV_FILE\"
        echo \"Using environment file: \$FINAL_ENV_FILE\"
    fi

    # Selection of compose command
    if [ -x /tmp/docker-compose-v2 ]; then
        DOCKER_CMD=\"/tmp/docker-compose-v2 \$ENV_ARG -f \$FINAL_COMPOSE_FILE -p $STACK_NAME\"
    elif docker compose version >/dev/null 2>&1; then
        DOCKER_CMD=\"docker compose \$ENV_ARG -f \$FINAL_COMPOSE_FILE -p $STACK_NAME\"
    elif command -v docker-compose >/dev/null 2>&1; then
        DOCKER_CMD=\"docker-compose \$ENV_ARG -f \$FINAL_COMPOSE_FILE -p $STACK_NAME\"
    else
        echo \"Error: Neither docker compose nor docker-compose found on remote host.\"
        exit 1
    fi
    
    echo \"Updating stack '$STACK_NAME' via: \$DOCKER_CMD\"
    \$DOCKER_CMD pull
    
    if [ -n \"$SERVICE_NAME\" ] && \$DOCKER_CMD config --services | grep -q \"^$SERVICE_NAME\$\"; then
        echo \"Updating service: $SERVICE_NAME\"
        \$DOCKER_CMD up -d $SERVICE_NAME
    else
        echo \"Updating full stack...\"
        \$DOCKER_CMD up -d
    fi
"

B64_DATA=$(echo "$COMPOSE_CONTENT" | base64 -w 0)
B64_ENV=""
if [ -n "$LOCAL_ENV_FILE" ] && [ -f "$LOCAL_ENV_FILE" ]; then
    B64_ENV=$(base64 -w 0 "$LOCAL_ENV_FILE")
fi
run_ssh "$REMOTE_HOST" "$REMOTE_USER" "$REMOTE_PASS" "BASE64_COMPOSE='$B64_DATA' BASE64_ENV='$B64_ENV' $DEPLOY_CMD"

echo "--- Stack '$STACK_NAME' deployment completed successfully ---"
