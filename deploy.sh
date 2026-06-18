#!/bin/bash
# deploy.sh — GigLens Play Store deployment + Pixel 10 sideload
# Usage: ./deploy.sh [changelog message]
# Example: ./deploy.sh "Fixed overlay pill screen targeting"
set -e

PACKAGE_NAME="com.augusteenterprise.giglens"
JSON_KEY="$HOME/giglens/google-play-api-key.json"
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
METADATA_DIR="metadata/en-US/changelogs"
PIXEL_IP="10.0.0.110"
PORT_FILE="$HOME/giglens/.pixel_port"

cd "$HOME/giglens"

# ── Load saved port ───────────────────────────────────────────────────────────
SAVED_PORT=""
if [ -f "$PORT_FILE" ]; then
    SAVED_PORT=$(cat "$PORT_FILE")
fi

# ── Try saved port first ──────────────────────────────────────────────────────
PIXEL_DEVICE=""
if [ -n "$SAVED_PORT" ]; then
    echo "📱 Trying saved Pixel port ($PIXEL_IP:$SAVED_PORT)..."
    adb connect "$PIXEL_IP:$SAVED_PORT" 2>&1
    if adb -s "$PIXEL_IP:$SAVED_PORT" get-state 2>/dev/null | grep -q "device"; then
        PIXEL_DEVICE="$PIXEL_IP:$SAVED_PORT"
        echo "✅ Connected to Pixel 10 at $PIXEL_DEVICE"
    else
        echo "⚠️  Saved port $SAVED_PORT no longer valid"
    fi
fi

# ── Prompt for new port if connection failed ──────────────────────────────────
if [ -z "$PIXEL_DEVICE" ]; then
    echo ""
    echo "📱 Enter Pixel 10 Wireless Debugging port"
    echo "   (Settings → Developer Options → Wireless Debugging)"
    if [ -n "$SAVED_PORT" ]; then
        read -p "   Port [$SAVED_PORT]: " NEW_PORT
        NEW_PORT="${NEW_PORT:-$SAVED_PORT}"
    else
        read -p "   Port (ENTER to skip sideload): " NEW_PORT
    fi

    if [ -n "$NEW_PORT" ]; then
        adb connect "$PIXEL_IP:$NEW_PORT" 2>&1
        if adb -s "$PIXEL_IP:$NEW_PORT" get-state 2>/dev/null | grep -q "device"; then
            PIXEL_DEVICE="$PIXEL_IP:$NEW_PORT"
            echo "$NEW_PORT" > "$PORT_FILE"
            echo "✅ Connected — port $NEW_PORT saved for next deploy"
        else
            echo "⚠️  Could not connect to $PIXEL_IP:$NEW_PORT — skipping sideload"
            echo "    Try: adb pair $PIXEL_IP:<pairing-port> first"
        fi
    else
        echo "   Sideload skipped"
    fi
fi
echo ""

# ── Bump version ──────────────────────────────────────────────────────────────
echo "🔢 Bumping version..."
CHANGELOG="${1:-Bug fixes and improvements.}"
git commit --allow-empty -m "release: $CHANGELOG"

# Get bumped version code
VERSION_CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
echo "📦 Building GigLens v$VERSION_CODE..."

# Write changelog
mkdir -p "$METADATA_DIR"
echo "$CHANGELOG" > "$METADATA_DIR/$VERSION_CODE.txt"
echo "📝 Changelog: $CHANGELOG"

# Build release AAB
echo "🔨 Building release AAB..."
./gradlew bundleRelease

# Deploy to Play Store internal track
echo "🚀 Uploading to Play Store Internal Testing..."
fastlane supply \
  --aab "$AAB_PATH" \
  --track internal \
  --package_name "$PACKAGE_NAME" \
  --json_key "$JSON_KEY"

echo "✅ Play Store upload done!"

# ── Sideload debug APK to Pixel 10 if connected ───────────────────────────────
if [ -n "$PIXEL_DEVICE" ]; then
    echo "🔨 Building debug APK for sideload..."
    ./gradlew assembleDebug
    adb -s "$PIXEL_DEVICE" install -r "$APK_PATH" \
        && echo "✅ Sideload complete — GigLens installed on Pixel 10" \
        || echo "⚠️  Sideload failed — try: adb -s $PIXEL_DEVICE uninstall $PACKAGE_NAME"
fi

# ── Post-deploy instructions ──────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GigLens v$VERSION_CODE deployed successfully"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  📲 TO INSTALL ON YOUR PHONE:"
echo "     1. Open Play Store on your Pixel"
echo "     2. Tap your profile icon (top right)"
echo "     3. Manage apps & device → Updates available"
echo "     4. Find GigLens and tap Update"
echo ""
echo "  🖥️  PLAY CONSOLE:"
echo "     https://play.google.com/console/u/0/developers/9166456463013911789/app/4974444943507625354/tracks/internal-testing"
echo ""
echo "  ✅ AFTER INSTALL — VALIDATE:"
echo "     1. Settings → confirm Accessibility mode is selected"
echo "     2. Toggle ON → confirm no dialog appears"
echo "     3. Confirm LIVE badge appears"
echo "     4. Open DoorDash → wait for offer → confirm pill appears once only"
echo "     5. Check analytics after shift for duplicate entries"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
