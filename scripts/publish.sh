#!/bin/bash
set -e

cd "$(dirname "$0")/.."
ROOT_DIR=$(pwd)

# Configuration
APP_NAME="ultrasonic"
MODULE="ultrasonic"

# Load optional configuration from .env
if [ -f ".env" ]; then
    while IFS='=' read -r key value; do
        if [[ ! $key =~ ^# && -n $key ]]; then
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            # Only export valid bash identifiers (skip keys with dots like sdk.dir)
            if [[ "$key" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
                export "$key=$value"
            fi
        fi
    done < .env
fi

# Build type (release or debug)
BUILD_TYPE=$(echo "${1:-debug}" | tr '[:upper:]' '[:lower:]')
if [[ "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "debug" ]]; then
    echo "Invalid build type: $BUILD_TYPE. Use 'release' or 'debug'."
    exit 1
fi

# Ensure DIST_DIR is set
DIST_DIR="${DIST_DIR:-dist}"

echo "--- Starting $BUILD_TYPE publish of $APP_NAME ---"

# 1. Extract Version Info from build.gradle
echo "--- Extracting version info ---"
VERSION_NAME=$(grep "versionName" $MODULE/build.gradle | head -n 1 | sed 's/.*"\(.*\)".*/\1/' || echo "unknown")
VERSION_CODE=$(grep "versionCode" $MODULE/build.gradle | head -n 1 | sed 's/[^0-9]*//g' || echo "0")

# 2. Publish Artifacts to dist/
echo "--- Publishing Artifacts to $DIST_DIR ---"
mkdir -p "$DIST_DIR"

# Extract Release Notes for current version
VER_QUERY="$VERSION_NAME"
if [ "$BUILD_TYPE" == "debug" ]; then
    VER_QUERY="$VERSION_NAME-$VERSION_CODE"
fi

echo "--- Extracting release notes for v$VER_QUERY ---"
# awk extracts the block, sed trims empty lines at start/end
NOTES=$(awk -v ver="$VER_QUERY" '$0 ~ "^## \\[" ver "\\]" {flag=1; next} /^## \[/ {flag=0} flag' RELEASE_NOTES.md | sed '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')

# If debug and empty, fallback to just VERSION_NAME
if [ "$BUILD_TYPE" == "debug" ] && [ -z "$NOTES" ]; then
    echo "Note: No specific notes found for $VER_QUERY, falling back to $VERSION_NAME"
    NOTES=$(awk -v ver="$VERSION_NAME" '$0 ~ "^## \\[" ver "\\]" {flag=1; next} /^## \[/ {flag=0} flag' RELEASE_NOTES.md | sed '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
fi

APK_PATH="$MODULE/build/outputs/apk/$BUILD_TYPE/$MODULE-$BUILD_TYPE.apk"
UNSIGNED_APK_PATH="$MODULE/build/outputs/apk/$BUILD_TYPE/$MODULE-$BUILD_TYPE-unsigned.apk"

if [ -f "$APK_PATH" ]; then
    SUFFIX=""
    [ "$BUILD_TYPE" == "debug" ] && SUFFIX="-debug"
    DEST="$DIST_DIR/$APP_NAME-v$VERSION_NAME-$VERSION_CODE$SUFFIX.apk"
    cp "$APK_PATH" "$DEST"
    echo "Successfully published: $DEST"
    # Save notes if found
    if [ -n "$NOTES" ]; then
        echo "$NOTES" > "${DEST%.apk}.txt"
    fi
elif [ -f "$UNSIGNED_APK_PATH" ]; then
    SUFFIX="-unsigned"
    [ "$BUILD_TYPE" == "debug" ] && SUFFIX="-debug-unsigned"
    DEST="$DIST_DIR/$APP_NAME-v$VERSION_NAME-$VERSION_CODE$SUFFIX.apk"
    cp "$UNSIGNED_APK_PATH" "$DEST"
    echo "Successfully published (UNSIGNED): $DEST"
    echo "Note: Configure signing in .env to produce signed builds."
    # Save notes if found
    if [ -n "$NOTES" ]; then
        echo "$NOTES" > "${DEST%.apk}.txt"
    fi
else
    echo "ERROR: Could not find resulting APK artifact at $APK_PATH. Did you run build.sh first?"
    exit 1
fi

# 3. Generate Index HTML for easy distribution
echo "--- Updating index.html and changelog.md ---"
cp CHANGELOG.md "$DIST_DIR/changelog.md"
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
    <h1>$APP_NAME Downloads <a href="changelog.md" style="font-size: 0.5em; vertical-align: middle; margin-left: 20px; text-decoration: none; color: #3498db;">[Changelog]</a></h1>
    
    <div id="current-version-banner" style="display: none; background: #e8f4fd; padding: 15px; border-radius: 12px; margin-bottom: 20px; border: 1px solid #3498db; color: #2980b9;">
        <svg style="width: 20px; height: 20px; vertical-align: middle; margin-right: 8px;" viewBox="0 0 24 24"><path fill="currentColor" d="M11,9H13V7H11M11,17H13V11H11M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z" /></svg>
        <strong>Your Current Version:</strong> <span id="current-v"></span> (<span id="current-c"></span>)
    </div>

    <ul class="apk-list">
EOF

# Use ls -t to sort by time, and loop through files
IS_FIRST=true
# Use a temporary file to avoid globbing issues in for loop if filenames have spaces
# We use || true to prevent script from exiting if no APKs are found
ls -t "$DIST_DIR"/*.apk > /tmp/apk_list.txt 2>/dev/null || touch /tmp/apk_list.txt
while IFS= read -r apk; do
    [ -z "$apk" ] || [ ! -f "$apk" ] && continue
    filename=$(basename "$apk")
    filedate=$(date -r "$apk" "+%Y-%m-%d %H:%M")
    
    LATEST_TAG=""
    if [ "$IS_FIRST" = true ]; then
        LATEST_TAG="<span class='tag tag-latest'>LATEST</span>"
        IS_FIRST=false
    fi
    
    # Try to find release notes
    NOTES_CONTENT=""
    notes_file="${apk%.apk}.txt"
    if [ -f "$notes_file" ]; then
        NOTES_CONTENT=$(cat "$notes_file")
    else
        # Extract version from filename like ultrasonic-v5.1.1-134.apk
        v=$(echo "$filename" | sed -n 's/.*-v\([0-9.]*\)-.*/\1/p')
        if [ -n "$v" ] && [ -f "RELEASE_NOTES.md" ]; then
            NOTES_CONTENT=$(awk -v ver="$v" '$0 ~ "^## \\[" ver "\\]" {flag=1; next} /^## \[/ {flag=0} flag' RELEASE_NOTES.md | sed '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
        fi
    fi

    RELEASE_NOTES_HTML=""
    if [ -n "$NOTES_CONTENT" ]; then
        # Simple formatting: escape HTML chars and handle Markdown-ish list/headers
        ESCAPED_NOTES=$(echo "$NOTES_CONTENT" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
        FORMATTED_NOTES=$(echo "$ESCAPED_NOTES" | sed 's/^### \(.*\)/<h4 style="margin: 10px 0 5px 0;">\1<\/h4>/g' | sed 's/^- \(.*\)/<li style="margin-left: 20px;">\1<\/li>/g')
        
        RELEASE_NOTES_HTML="<details style='margin-top: 10px; font-size: 0.9em; color: #444;'><summary style='cursor: pointer; color: #3498db;'>Release Notes</summary><div style='margin-top: 8px; border-top: 1px solid #eee; padding-top: 8px;'>$FORMATTED_NOTES</div></details>"
    fi

    cat >> "$DIST_DIR/index.html" <<EOF
        <li class="apk-item">
            <a class="apk-link" href="$filename" download>$filename $LATEST_TAG</a>
            <div class="apk-info">
                <span>Built: $filedate</span>
                <span class="tag">APK</span>
            </div>
            $RELEASE_NOTES_HTML
        </li>
EOF
done < /tmp/apk_list.txt

cat >> "$DIST_DIR/index.html" <<EOF
    </ul>
    <footer style="margin-top: 40px; text-align: center; color: #95a5a6; font-size: 0.8em;">
        <p>Generated by publish.sh on $(date "+%Y-%m-%d %H:%M:%S")</p>
    </footer>

    <script>
        const urlParams = new URLSearchParams(window.location.search);
        const v = urlParams.get('v');
        const c = urlParams.get('c');
        if (v && c) {
            document.getElementById('current-v').textContent = v;
            document.getElementById('current-c').textContent = c;
            document.getElementById('current-version-banner').style.display = 'block';
            
            // Highlight the exact match if it exists
            const links = document.querySelectorAll('.apk-link');
            links.forEach(link => {
                if (link.textContent.includes('-v' + v + '-' + c)) {
                    link.parentElement.style.borderColor = '#27ae60';
                    link.parentElement.style.background = '#f0fff4';
                    const tag = document.createElement('span');
                    tag.className = 'tag';
                    tag.style.background = '#27ae60';
                    tag.style.color = 'white';
                    tag.textContent = 'INSTALLED';
                    link.appendChild(tag);
                }
            });
        }
    </script>
</body>
</html>
EOF

echo "--- All publishes completed ---"
echo ""
echo "Quick Distribute: Run the following to host your APKs on your local network:"
echo "cd $DIST_DIR && python3 -m http.server 8080"
echo "Then visit http://$(hostname -I | awk '{print $1}'):8080 on your phone."
