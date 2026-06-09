#!/bin/bash
# deploy.sh — GigLens Play Store deployment script
# Usage: ./deploy.sh [changelog message]
# Example: ./deploy.sh "Fixed overlay pill screen targeting"

set -e

PACKAGE_NAME="com.augusteenterprise.giglens"
JSON_KEY="$HOME/giglens/google-play-api-key.json"
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
METADATA_DIR="metadata/en-US/changelogs"

cd "$HOME/giglens"

# Get current version code
VERSION_CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
echo "📦 Building GigLens v$VERSION_CODE..."

# Write changelog
mkdir -p "$METADATA_DIR"
CHANGELOG="${1:-Bug fixes and improvements.}"
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

echo "✅ Done! Check Play Console for the new release."
