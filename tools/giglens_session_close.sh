#!/bin/bash
# giglens_session_close.sh — Full session close in one command
# Author: Claude (Anthropic) | Auguste Enterprise
# Usage: bash /home/poppa/giglens/tools/giglens_session_close.sh

REPO_ROOT="/home/poppa/giglens"
TRANSCRIPT="$REPO_ROOT/session_transcript.txt"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║     GigLens — Paste Session Transcript       ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  1. Go to your Claude.ai chat"
echo "  2. Select all (Ctrl+A) and copy (Ctrl+C)"
echo "  3. Come back here and paste (Ctrl+V), then press Ctrl+D"
echo ""
echo "  Waiting for transcript (paste now)..."
echo "────────────────────────────────────────────────"

# Read from stdin into transcript file
cat > "$TRANSCRIPT"

echo "────────────────────────────────────────────────"

CHAR_COUNT=$(wc -c < "$TRANSCRIPT")
if [ "$CHAR_COUNT" -lt 100 ]; then
    echo "❌ Only $CHAR_COUNT bytes received — too short. Try again."
    rm "$TRANSCRIPT"
    exit 1
fi

echo "✅ Transcript captured ($CHAR_COUNT bytes)"
echo ""

# Hand off to session_end.sh
bash "$REPO_ROOT/tools/session_end.sh" "$@"
