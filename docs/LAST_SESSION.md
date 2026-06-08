# GigLens — Session Handover
**Date:** 2026-06-07
**Version at session end:** 0.1.117
**Build state:** PASSING
**Conducted by:** Claude (auto-generated via tools/gen_handover.py)

---

# GigLens — Session Handover
**Date:** 2026-06-07  
**Version at session end:** 0.1.117  
**Build state:** PASSING  
**Conducted by:** Claude (Anthropic)

---

## What Was Completed This Session

- ✅ **Camera pill now fires `SHOW_CAMERA` intent on live DoorDash offers**
  - Root cause: `CAPTURE_COOLDOWN_MS` early return in `OfferDetectorService.signalCapture()` blocked fingerprint check before `SHOW_CAMERA` could fire
  - Fix: moved cooldown gate to **after** `sendBroadcast(Intent(SHOW_CAMERA))` — cooldown now only throttles repeated `signalCapture()` calls, not the camera trigger itself
  - Logs confirmed: `NEW offer screen detected → isOffer=true → Offer screen confirmed — sending SHOW_CAMERA → Widget morphed to CAMERA state`

- ✅ **Overlay window recovery on process restart**
  - Root cause: `START_STICKY` reused service instance without calling `onCreate()` — overlay window lost after process death (swipe-away or force-stop)
  - Fix: added window validity check in `OfferOverlayService.onStartCommand()` — calls `windowManager.updateViewLayout()` to detect lost window, re-adds silently on exception
  - No service restart needed, no pill flicker, ~1ms overhead on healthy window
  - Recovery gated on `WIDGET_ENABLED` DB check — pill only re-appears if toggle is on

- ✅ **Settings toggle persistence fixed — single source of truth**
  - Root cause: `SettingsActivity` wrote to `AppConfigKeys.WIDGET_ENABLED` in Room DB, `MainActivity.onResume()` read from `SharedPreferences("giglens_ui", "floating_button_enabled")` — two separate stores out of sync
  - Fix: `MainActivity.onResume()` now reads `WIDGET_ENABLED` from DB via coroutine, removed SharedPreferences fallback
  - Toggle saves instantly on flip (no Save button required), state syncs across all app lifecycles (force-stop, reboot, process restart)

- ✅ **`startForegroundService()` compliance on Android O+**
  - Root cause: `MainActivity.onResume()` and `SettingsActivity` used `startService()` on Android 12+ instead of `startForegroundService()`
  - Fix: unified to always use `startForegroundService()` on Android O+ (SDK 26+)
  - `OfferOverlayService` and `ScreenCaptureService` no longer killed before `onStartCommand()` fires

- ✅ **DB upsert fix — toggle state survives `pm clear`**
  - Root cause: `AppConfigDao.setValue()` used `UPDATE` only — after `pm clear`, no rows existed, `UPDATE` returned 0 silently
  - Fix: changed to upsert pattern — `INSERT INTO app_config (key, value) VALUES (:key, :value) ON CONFLICT(key) DO UPDATE SET value = :value`
  - Toggle now saves correctly even on fresh DB (first launch or after data clear)

- ✅ **Removed `runBlocking` deadlock from service startup**
  - Root cause: `OfferOverlayService.onStartCommand()` called `runBlocking { appConfigDao.getValue(WIDGET_ENABLED) }` on main thread — Room dispatcher queued DB read, main thread waited for dispatcher, dispatcher waited for main thread
  - Fix: pass `widget_enabled` as intent extra from `MainActivity` and `SettingsActivity` — no DB read needed in `onStartCommand()`
  - New helper: `setWidgetEnabled(context, enabled)` writes DB + sends `START_OVERLAY` intent with extra in single call

- ✅ **MediaProjection crash protection + restart notification**
  - Root cause: `ScreenCaptureService.createDisplayResources()` threw exception when MediaProjection token expired mid-shift — crashed entire process, no recovery path
  - Fix: wrapped `createVirtualDisplay()` in try-catch, records exception to Crashlytics, calls `showRestartNotification()`
  - Restart notification persists through process death, one tap launches `MainActivity` with `restart_capture=true` intent extra
  - Notification auto-dismisses when screen capture successfully restarts

- ✅ **Firebase Crashlytics crash upload reliability**
  - Root cause: process killed too fast after exception — crashes recorded but never uploaded to Firebase console
  - Fix: added `FirebaseCrashlytics.getInstance().sendUnsentReports()` in catch block immediately after `recordException()`
  - Also added `sendUnsentReports()` to `ScreenCaptureService.onCreate()` to flush crashes from previous session on service start

- ✅ **Camera pill visual rendering fix applied**
  - Root cause: `windowManager.updateViewLayout()` ran successfully but GPU compositor didn't redraw view on background thread trigger
  - Fix: added `rootView.invalidate()` + `rootView.requestLayout()` after `updateViewLayout()` in `OfferOverlayService.updateWidget()` to force GL layer redraw
  - **Not yet tested on live offer** — logs show state change, driver needs to confirm visual change on screen

- ✅ **Widget state now respects toggle on all service starts**
  - Root cause: `OfferOverlayService.onCreate()` called `showWidget()` unconditionally — pill appeared even when toggle was off
  - Fix: moved `showWidget()` into `onStartCommand()`, gated on `intent.getBooleanExtra("widget_enabled", false)`
  - Pill only shows when toggle is on, hidden when toggle is off (verified across force-stop, reboot, swipe-away)

## What Was Left Incomplete

- ⏳ **Camera pill visual state not yet confirmed on real offer** — logs show `morphed to CAMERA state` but driver has not seen 📷 render on screen during test (invalidate fix applied but untested)
- ⏳ **Auto-shutdown when DoorDash closes** — discussed but not implemented (60-second countdown when DoorDash app closes, auto-disable toggle + stop services)
- ⏳ **Settings UI for gas price, MPG, wear & tear** — backlog, build after camera confirmed working visually
- ⏳ **"Send Crash Report" button in SettingsActivity** — Firebase Crashlytics call ready, just needs UI button wired

## Known Broken (do not ignore)

- 🔴 **Camera pill state change may not render on screen** — logs show `updateWidget rendering state=CAMERA` but driver did not see visual change during last test
  - `windowManager.updateViewLayout()` ran without exception
  - `invalidate()` + `requestLayout()` fix applied but not yet tested on live offer
  - If still not rendering, check overlay z-order: `adb shell dumpsys window windows | grep -B2 -A8 "giglens.*overlay\|APPLICATION_OVERLAY"`

## Next Session — Start Here

**First task:** Test camera pill visual rendering on live offer after invalidate fix  
**GitHub Issue:** create new issue "Camera pill state not rendering visually"  
**Context needed:**
- Pill showing at app start ✅
- `SHOW_CAMERA` intent firing ✅
- `morphed to CAMERA state` logging ✅
- `updateViewLayout()` succeeding ✅
- Visual state change not confirmed 🔴

**Test plan:**
```bash
# Wait for live DoorDash offer
# Observe pill — should morph from 📊 (idle) to 📷 (camera)
# If not rendering, check logs:
adb logcat | grep -iE "updateWidget|morphed|CAMERA"
# If logs show state change but pill doesn't render, check z-order:
adb shell dumpsys window windows | grep -B2 -A8 "APPLICATION_OVERLAY" | grep -iE "mBaseLayer|isOnScreen|giglens"
```

**Second task:** Implement auto-shutdown when DoorDash closes  
- 60-second `Handler.postDelayed()` countdown when DoorDash package leaves foreground
- Cancel countdown if D

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Pixel 10 XL (10.0.0.110:5555): run `adb connect 10.0.0.110:5555` to verify

## Files Changed This Session

- (no changes detected)

---
*Auto-generated by tools/gen_handover.py — next developer read SESSION_PROTOCOL.md first.*
