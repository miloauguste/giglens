#!/bin/bash
# session_end.sh — GigLens End-of-Session Wrapper
# Author: Claude (Anthropic) | Auguste Enterprise
# Usage: bash /home/poppa/giglens/tools/session_end.sh [--no-build]

set -e

REPO_ROOT="/home/poppa/giglens"
TRANSCRIPT="$REPO_ROOT/session_transcript.txt"
VENV_PYTHON="$REPO_ROOT/tools/venv/bin/python3"
HANDOVER_SCRIPT="$REPO_ROOT/tools/gen_handover.py"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║     GigLens — End of Session Workflow        ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

if [ ! -f "$TRANSCRIPT" ]; then
    echo "❌ No transcript at $TRANSCRIPT"
    echo "   Paste your chat: cat > $TRANSCRIPT  then Ctrl+D"
    read -p "   Press Enter when ready, or Ctrl+C to abort..."
fi

[ ! -f "$TRANSCRIPT" ] && echo "❌ No transcript. Aborting." && exit 1

CHAR_COUNT=$(wc -c < "$TRANSCRIPT")
echo "✅ Transcript found ($CHAR_COUNT bytes)"
echo ""
echo "── Generating handover..."

BUILD_FLAG=""
[[ "$1" == "--no-build" ]] && BUILD_FLAG="--no-build"
"$VENV_PYTHON" "$HANDOVER_SCRIPT" $BUILD_FLAG

echo ""
echo "── Changed files:"
cd "$REPO_ROOT" && git status --short
echo ""
echo "── LAST_SESSION.md preview:"
echo "────────────────────────────"
head -40 "$REPO_ROOT/docs/LAST_SESSION.md"
echo ""

read -p "   Looks good? [y/N] " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "   Edit docs/LAST_SESSION.md then commit manually."
    exit 0
fi

TODAY=$(date +%Y-%m-%d)
DEFAULT_MSG="chore: session handover $TODAY"
read -p "   Commit message [$DEFAULT_MSG]: " COMMIT_MSG
COMMIT_MSG="${COMMIT_MSG:-$DEFAULT_MSG}"

git add -A
git commit -m "$COMMIT_MSG"
git push

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  ✅ Session closed and pushed to GitHub      ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "   Next session: cd $REPO_ROOT && git pull && cat docs/LAST_SESSION.md"
