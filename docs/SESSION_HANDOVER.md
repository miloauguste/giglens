# GigLens — Session Handover
**Date:** 2026-06-04
**Version at session end:** 0.1.94 → latest push 31cdb4a
**Build state:** PASSING
**Conducted by:** Claude (Anthropic)

---

## What Was Completed This Session

- ✅ **Bug 1: Camera requires manual intervention — fixed**
  - OfferOverlayService now started immediately when ScreenCaptureService launches
  - Pill visible from shift start without MainActivity interaction

- ✅ **Bug 2: Pill disappears mid-shift — fixed**
  - Auto-revert to IDLE disabled — pill persists entire shift
  - Countdown timer removed from pill label

- ✅ **Bug 3: Camera blinks randomly — fixed**
  - SHOW_CAMERA gated on confirmed offer screen detection
  - HIDE_CAMERA sent when offer screen disappears
  - No longer fires on map redraws, navigation, earnings screen

- ✅ **OWASP scan unblocked**
  - CVE-2020-29582 and CVE-2020-8908 suppressed as false positives
  - Both confirmed not applicable to GigLens codebase

- ✅ **Desk test passed on Pixel 10 Pro XL**
  - Grey pill appears on toggle ON ✅
  - No camera blink on non-offer screens ✅
  - Live offer detection pending real shift test

## What Was Left Incomplete

- ⏳ Live shift test — camera button on real offer not yet validated

## Known Broken (do not ignore)

- 🔴 **NVD API key** — configured, OWASP now running on push

## Next Session — Start Here

**First task:** Live DoorDash shift — validate camera button on real offer, OCR accuracy, scoring feel
**Check after shift:**
- Camera button appeared automatically on offer screen
- Pill stayed visible entire shift
- No random camera blinks
- OCR accuracy on pay amount, distance, restaurant name

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Camera on offer screen | ✅ Fixed | Gated on looksLikeOfferScreen() |
| Pill persists shift | ✅ Fixed | Auto-revert disabled |
| Random camera blink | ✅ Fixed | SHOW/HIDE_CAMERA gated on detection |
| OWASP scan | ✅ Clean | CVE-2020-29582, CVE-2020-8908 suppressed |
| Live shift OCR test | 🟡 Pending | Desk test passed, real offer not yet tested |
| OCR scoring weights | 🟡 In progress | No Settings UI yet |

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Pixel 10 XL: port changes on reboot — check Wireless Debugging each session

---
*Next developer: read SESSION_PROTOCOL.md first, then go straight to live shift test.*
