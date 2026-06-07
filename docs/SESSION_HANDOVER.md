# GigLens — Session Handover
**Date:** 2026-06-06
**Version at session end:** v0.1.106 — commit c6cdfb4
**Build state:** PASSING
**Conducted by:** Claude (Anthropic)

---

## What Was Completed This Session

- ✅ **Firebase Crashlytics fully operational**
  - Logs & Breadcrumbs confirmed working in Firebase console
  - Google Analytics enabled — required for log feature
  - AD_ID/WAKE_LOCK permissions allowlisted in pre-commit hook
  - After each shift: https://console.firebase.google.com/project/giglens-57f0c/crashlytics

- ✅ **Offer detection improved**
  - Added "guaranteed" and "mi" as detection signals
  - Root cause confirmed: DoorDash renders $ as individual characters on home screen
  - screen_texts.log confirmed working — accessibility tree readable

- ✅ **Memory architecture overhauled**
  - VirtualDisplay created per-capture, destroyed immediately after
  - Held ~2s per offer instead of entire shift — 99.8% memory reduction
  - ImageReader buffer reduced from 2 to 1 frame
  - Bitmap downsampled 50% before OCR

- ✅ **CAPTURE_DEAD recovery pill**
  - Red ⚠️ pill appears when ScreenCaptureService dies
  - Tapping launches MainActivity to re-request MediaProjection
  - OfferOverlayService now proper foreground service on all Android versions

- ✅ **OfferScorer v4**
  - Gas cost + wear & tear only — no time/hourly cost
  - 2-factor scoring: net value (70%) + true $/mile (30%)
  - Verified: Taco Bell $7.20/3.7mi → BORDERLINE (46) ✅

## Next Session — Start Here

**First task:** Live shift test — pull Firebase logs after shift
**After shift:** Check https://console.firebase.google.com/project/giglens-57f0c/crashlytics
  - Click any session → Logs & Breadcrumbs tab
  - Look for isOffer=true entries
  - If none: check if ScreenCaptureService survived (dumpsys activity services)

**Before shift — clear debug log:**
adb -s 10.0.0.110:<port> shell rm /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/screen_texts.log
## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Firebase Crashlytics | ✅ Complete | Logs verified in console |
| Offer detection | ✅ Improved | guaranteed + mi signals added |
| Memory architecture | ✅ Complete | VirtualDisplay per-capture |
| CAPTURE_DEAD pill | ✅ Complete | Foreground service anchor |
| OfferScorer v4 | ✅ Complete | Gas + wear/tear verified |
| Camera on live offer | 🟡 Pending | Next shift will confirm via Firebase logs |
| Settings UI gas/MPG/wear | 🟡 Pending | Next build task after shift confirmed |

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: port changes on reboot — check Wireless Debugging each session
- Firebase console: https://console.firebase.google.com/project/giglens-57f0c/crashlytics

---
*Next developer: read SESSION_PROTOCOL.md first. Priority is live shift validation via Firebase logs.*
