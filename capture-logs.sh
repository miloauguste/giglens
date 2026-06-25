#!/bin/bash
# capture-logs.sh — Capture GigLens logcat to a persistent file on milo-dev.
#
# WHY THIS EXISTS:
#   Android logcat is a kernel ring buffer (~256 KB). On a phone running DoorDash
#   + Play Services + other apps, that fills in 3–5 minutes. Any logs from a shift
#   are gone well before post-shift analysis begins. This script redirects the
#   GigLens-relevant tags to a timestamped file so logs survive across sessions.
#
# USAGE:
#   ./capture-logs.sh           # connect to Pixel, start capture in background
#   ./capture-logs.sh --stop    # kill the background capture
#   ./capture-logs.sh --tail    # tail the current session log
#   ./capture-logs.sh --list    # list saved log files

set -euo pipefail

LOGS_DIR="$HOME/giglens/logs"
PID_FILE="$LOGS_DIR/.capture.pid"
DEVICE_SERIAL="10.0.0.110:$(cat "$HOME/giglens/.pixel_port" 2>/dev/null || echo 0)"

TAGS="GigLensApp:D ShareReceiver:D OfferParser:D AccessibilityReceiver:D \
      TownEstim:D PinDetect:D OfferOverlay:D OfferDetector:D ScreenCapture:D \
      DeliveryTown:D *:S"

mkdir -p "$LOGS_DIR"

case "${1:---start}" in

  --stop)
    if [[ -f "$PID_FILE" ]]; then
      kill "$(cat "$PID_FILE")" 2>/dev/null && echo "Capture stopped." || echo "Process already gone."
      rm -f "$PID_FILE"
    else
      echo "No capture running."
    fi
    ;;

  --tail)
    LATEST=$(ls -t "$LOGS_DIR"/shift_*.log 2>/dev/null | head -1)
    if [[ -z "$LATEST" ]]; then
      echo "No log files found in $LOGS_DIR"
      exit 1
    fi
    echo "Tailing $LATEST"
    tail -f "$LATEST"
    ;;

  --list)
    ls -lh "$LOGS_DIR"/shift_*.log 2>/dev/null || echo "No log files in $LOGS_DIR"
    ;;

  --start|*)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "Capture already running (PID $(cat "$PID_FILE")). Use --stop first."
      exit 0
    fi

    adb connect "$DEVICE_SERIAL" > /dev/null 2>&1 || true
    LOGFILE="$LOGS_DIR/shift_$(date +%Y%m%d_%H%M).log"

    # Clear the on-device buffer so the file starts clean (no stale entries)
    adb -s "$DEVICE_SERIAL" logcat -c 2>/dev/null || true

    # Increase on-device buffer to 16 MB for the session to reduce mid-shift gaps
    adb -s "$DEVICE_SERIAL" logcat -G 16M 2>/dev/null || true

    adb -s "$DEVICE_SERIAL" logcat -v time $TAGS >> "$LOGFILE" &
    echo $! > "$PID_FILE"

    echo "Capturing GigLens logs → $LOGFILE (PID $!)"
    echo "Run './capture-logs.sh --stop' when shift ends."
    ;;
esac
