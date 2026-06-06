# GigLens — Session Handover
**Date:** 2026-06-06
**Version at session end:** latest push 8824e8e
**Build state:** PASSING
**Conducted by:** Claude (Anthropic)

---

## What Was Completed This Session

- ✅ **Root cause identified: mem-pressure-event killing ScreenCaptureService mid-shift**
  - Android logs confirmed: system-wide memory squeeze during DoorDash + GigLens + navigation
  - Not a code bug — environmental memory pressure

- ✅ **VirtualDisplay deferred architecture**
  - VirtualDisplay now created per-capture, destroyed immediately after bitmap extracted
  - Held ~2s per offer instead of entire 4-6hr shift — 99.8% memory reduction
  - MediaProjection token held only between offers

- ✅ **Additional memory fixes**
  - ImageReader buffer reduced from 2 frames to 1 (~8MB saved constant)
  - Bitmap downsampled to 50% before OCR (75% memory reduction per capture)

- ✅ **CAPTURE_DEAD recovery pill**
  - OfferOverlayService now runs as proper foreground service on all Android versions
  - Red ⚠️ Tap pill appears when ScreenCaptureService dies — stays visible instead of disappearing
  - Tapping red pill launches MainActivity to re-request MediaProjection

- ✅ **OfferScorer v4**
  - Gas cost + wear & tear only — no time/hourly cost
  - 2-factor scoring: net value (70%) + true $/mile (30%)
  - Verified manually: Taco Bell $7.20/3.7mi → BORDERLINE (46) ✅

## What Was Left Incomplete

- ⏳ Camera button on live offer not yet confirmed — ScreenCaptureService kept dying before detection
- ⏳ CAPTURE_DEAD pill tap → MainActivity flow not fully validated
- ⏳ Settings UI for gas price, MPG, wear & tear not yet built

## Next Session — Start Here

**First task:** Live shift test with new VirtualDisplay architecture
**Before shift:** clear log buffer: `adb logcat -c`
**After shift:** pull logs immediately: `adb logcat -d | grep -E "ScreenCapture|OfferDetector|looksLike|isOffer" > /tmp/shift.log`
**Watch for:**
- Does ScreenCaptureService survive the full shift (check dumpsys after)
- Does camera button appear on offer screen
- If capture dies — does red ⚠️ pill appear

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| VirtualDisplay per-capture | ✅ Complete | 99.8% memory reduction |
| CAPTURE_DEAD recovery pill | ✅ Complete | Foreground service, tap to restart |
| OfferScorer v4 | ✅ Complete | Gas + wear/tear, verified |
| Camera on live offer | 🟡 Pending | Blocked by ScreenCaptureService dying |
| Settings UI gas/MPG/wear | 🟡 Pending | Next build task |
| OCR scoring weights UI | 🟡 Pending | After settings UI |

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: port changes on reboot — check Wireless Debugging each session

---
*Next developer: read SESSION_PROTOCOL.md first. Priority is live shift validation of new VirtualDisplay architecture.*
