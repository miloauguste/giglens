#!/bin/bash
# install.sh — GigLens ADB install using release signing
# Usage: ./install.sh [device-ip:port]
# Example: ./install.sh 10.0.0.110:43210

set -e

GIGLENS_DIR="/home/poppa/giglens"
APK_PATH="app/build/outputs/apk/release/app-release.apk"

cd "$GIGLENS_DIR"

# Prompt for port if not provided
if [ -z "$1" ]; then
    read -rp "📱 Enter device IP:port (e.g. 10.0.0.110:43210): " DEVICE
else
    DEVICE="$1"
fi

echo "📱 Connecting to $DEVICE..."
adb connect "$DEVICE"

# Build release APK with release signing
echo "🔨 Building release APK..."
export GIGLENS_STORE_PASSWORD GIGLENS_KEY_PASSWORD
./gradlew assembleRelease 2>&1 | tail -5

# Install via ADB
echo "📲 Installing on device..."
adb -s "$DEVICE" install -r "$APK_PATH"

VERSION=$(cat version.txt)
echo "✅ GigLens $VERSION installed via ADB (release signed)"
