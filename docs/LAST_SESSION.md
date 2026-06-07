# GigLens — Session Handover
**Date:** 2026-06-07
**Version at session end:** 0.1.111
**Build state:** PASSING
**Conducted by:** Claude (auto-generated via tools/gen_handover.py)

---

# GigLens — Session Handover
**Date:** 2026-06-07
**Version at session end:** 0.1.115
**Build state:** PASSING
**Conducted by:** Claude (auto-generated via tools/gen_handover.py)

---

## What Was Completed This Session

- ✅ **Camera pill now fires on live DoorDash offers — end-to-end pipeline working**
  - Root cause: `CAPTURE_COOLDOWN_MS` early return blocked fingerprint check before `SHOW_CAMERA` fired
  - Fix: moved cooldown gate to after `SHOW_CAMERA` send, only throttles `signalCapture()`
  - Logs confirmed: `NEW offer screen detected`, `Offer screen confirmed — sending SHOW_CAMERA`, `Widget morphed to CAMERA state`
  - Detection signals working: `accept=true decline=true dollar=true guaranteed=true mi=true → isOffer=true`

- ✅ **OfferOverlayService overlay window recovery on process restart**
  - Root cause: `START_STICKY` reused service instance without calling `onCreate()`, overlay window lost after process death
  - Fix: added window validity check in `onStartCommand()` — `windowManager.updateViewLayout()` detects lost window, re-adds silently
  - No service restart needed, no pill flicker, ~1ms overhead on healthy window
  - Gated on `WIDGET_ENABLED` DB check — pill only re-appears if toggle is on

- ✅ **Settings toggle now saves correctly and syncs with MainActivity**
  - Root cause: `SettingsActivity` wrote to `AppConfigKeys.WIDGET_ENABLED` in DB, `MainActivity.onResume()` read from `SharedPreferences("giglens_ui", "floating_button_enabled")`
  - Fix: `MainActivity.onResume()` now reads `WIDGET_ENABLED` from DB via coroutine
  - Single source of truth — toggle saves instantly on flip, no Save button required
  - Pill respects toggle state on all app lifecycles (force-stop, reboot, process restart)

- ✅ **`startForegroundService()` fix for Android 16**
  - Root cause: `MainActivity.onResume()` used `startService()` on Android 12+ instead of `startForegroundService()`
  - Fix: unified to always use `startForegroundService()` on Android O+ (SDK 26+)
  - `OfferOverlayService` no longer killed before `onStartCommand` fires

- ✅ **Firebase Crashlytics + screen_texts.log still operational**
  - Detection logs confirmed: `isOffer=true` entries in `/sdcard/.../debug/screen_texts.log`
  - Offer data parsing working: `$7.00 guaranteed (incl. tips) | 3.7 mi | McDonald's`
  - `OfferScorer v4` still active: gas + wear/tear only, 2-factor net value + true $/mile

- ✅ **Permissions verified and working**
  - `SYSTEM_ALERT_WINDOW: allow` (overlay permission)
  - `PROJECT_MEDIA: allow` (MediaProjection)
  - `captureRunning=true` steady — no `CAPTURE_DEAD` spam after capture starts

## What Was Left Incomplete

- ⏳ **Camera pill visual state not yet confirmed on real offer** — logs show `morphed to CAMERA state` but driver did not see 📷 on screen during test
- ⏳ **Auto-shutdown when DoorDash closes** — discussed but not implemented (60-second countdown when DoorDash app closes, auto-disable toggle + stop services)
- ⏳ **Settings UI for gas price, MPG, wear & tear** — backlog, build after camera confirmed working visually

## Known Broken (do not ignore)

- 🔴 **Camera pill state change not rendering on screen** — logs show `updateWidget rendering state=CAMERA` but driver sees no visual change
  - `windowManager.updateViewLayout()` ran without exception
  - Overlay window exists and is healthy (`updateViewLayout` succeeded)
  - Likely GL/hardware layer invalidation issue or view z-order problem
  - Next step: add `rootView.invalidate()` after `updateViewLayout()` or check `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` z-order vs DoorDash window

## Next Session — Start Here

**First task:** Add `rootView.invalidate()` to `updateWidget()` after state change to force GL layer redraw
```kotlin
// OfferOverlayService.kt — updateWidget()
windowManager.updateViewLayout(rootView!!, layoutParams!!)
rootView!!.invalidate()  // ← force GL layer redraw
```

**Second task:** Test camera pill visual change on live offer after invalidate fix

**Third task (if still not rendering):** Check overlay z-order
```bash
adb -s 10.0.0.110:<port> shell dumpsys window windows | grep -B2 -A8 "giglens.*overlay\|APPLICATION_OVERLAY" | grep -iE "mBaseLayer|isOnScreen"
```

**Context needed:**
- Pill showing at app start ✅
- Pill persisting entire shift ✅
- `updateWidget()` running correctly ✅
- `morphed to CAMERA state` logging ✅
- **But visual state not changing on screen** 🔴

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection pipeline | ✅ Complete | `isOffer=true` firing reliably on DoorDash offers |
| `SHOW_CAMERA` intent signal | ✅ Complete | Logs confirm intent sent and received |
| `OfferOverlayService.onStartCommand()` | ✅ Complete | `morphed to CAMERA state` confirmed |
| Window recovery on restart | ✅ Complete | Lost window re-added silently, gated on `WIDGET_ENABLED` |
| Settings toggle persistence | ✅ Complete | DB sync working, toggle saves instantly |
| Camera pill visual rendering | 🔴 Broken | State changes in code but not on screen |
| Auto-shutdown on DoorDash close | 🟡 Pending | Discussed, not yet implemented |
| Offer history screen | 🟡 Pending | Data in DB, RecyclerView needed |
| Settings UI gas/MPG/wear | 🟡 Pending | Build after camera confirmed |

## Decisions Made This Session

- **Source of truth for widget toggle:** `AppConfigKeys.WIDGET_ENABLED` in DB, not `SharedPreferences` — all code now reads from DB
- **Window recovery approach:** Detect and re-add lost window in `onStartCommand()` instead of service restart — better battery, no flicker
- **Auto-shutdown design:** 60-second countdown when DoorDash closes, auto-disable toggle + stop services — deferred to next session

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: port 43013 this session (changes on reboot — check Wireless Debugging)
- Firebase: https://console.firebase.google.com/project/giglens-57f0c/crashlytics

## Files Changed This Session

- `OfferDetectorService.kt` — moved `CAPTURE_COOLDOWN_MS` check after `SHOW_CAMERA` send
- `OfferOverlayService.kt` — added window recovery in `onStartCommand()`, gated on `WIDGET_ENABLED` DB check, added `runBlocking` DB read
- `MainActivity.kt` — switched `onResume()` from `SharedPreferences` to DB for `WIDGET_ENABLED`, wrapped in `lifecycleScope.launch`
- `MainActivity.kt` — unified `startForegroundService()` for Android O+

---
*Auto-generated by tools/gen_handover.py — next developer read SESSION_PROTOCOL.md first.*

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Pixel 10 XL (10.0.0.110:5555): run `adb connect 10.0.0.110:5555` to verify

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt
- app/src/main/java/com/augusteenterprise/giglens/ui/MainActivity.kt

---
*Auto-generated by tools/gen_handover.py — next developer read SESSION_PROTOCOL.md first.*
