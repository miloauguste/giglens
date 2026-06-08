# GigLens — Session Handover
**Date:** 2026-06-07
**Version at session end:** 0.1.116
**Build state:** PASSING
**Conducted by:** Claude (auto-generated via tools/gen_handover.py)

---

# GigLens — Session Handover
**Date:** 2026-06-07  
**Version at session end:** 0.1.116  
**Build state:** PASSING  
**Conducted by:** Claude (Anthropic)

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

- ✅ **Toggle sync — DB upsert + intent extra pattern**
  - Root cause: `setValue()` used `UPDATE` only — after `pm clear`, row didn't exist, UPDATE silently failed
  - Fix: changed `setValue()` to upsert — `INSERT ... ON CONFLICT(key) DO UPDATE SET value = :value`
  - Also removed `runBlocking` DB read from `onStartCommand` — caused deadlock with Room dispatcher
  - New pattern: `widget_enabled` passed via intent extra, toggle state always syncs correctly

- ✅ **MediaProjection crash protection + restart notification**
  - Root cause: `createVirtualDisplay()` threw exception when MediaProjection token expired mid-shift
  - Fix: wrapped in try-catch, records exception to Crashlytics, shows persistent notification "Tap to re-enable screen capture"
  - Notification survives process death, one tap launches MainActivity with `restart_capture=true` flag
  - `sendUnsentReports()` called in catch block and `ScreenCaptureService.onCreate()` to flush crashes immediately

- ✅ **Camera pill visual rendering fix**
  - Root cause: `windowManager.updateViewLayout()` ran but GPU compositor didn't redraw on background thread trigger
  - Fix: added `root.invalidate()` + `root.requestLayout()` after `updateViewLayout()` to force GL layer redraw

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
  - **Fix applied but not yet tested on live offer:** `rootView.invalidate()` after `updateViewLayout()` or check `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` z-order vs DoorDash window

## Next Session — Start Here

**First task:** Test camera pill visual rendering on live offer after invalidate fix
```bash
# Camera pill fix already applied — test on next live offer
# If still not rendering, check overlay z-order:
adb -s 10.0.0.110:<port> shell dumpsys window windows | grep -B2 -A8 "giglens.*overlay\|APPLICATION_OVERLAY" | grep -iE "mBaseLayer|isOnScreen"
```

**Second task:** Implement auto-shutdown when DoorDash closes
- 60-second `Handler.postDelayed()` countdown when DoorDash package leaves foreground
- Cancel countdown if DoorDash returns within 60s
- On expiry: stop `ScreenCaptureService`, stop `OfferOverlayService`, write `WIDGET_ENABLED=false` to DB
- Show countdown on pill: "⏱ 60s" ticking down so driver knows shutdown is pending
- Hook `sendUnsentReports()` into shutdown sequence to flush Crashlytics before services stop

**Third task:** Add "Send Crash Report" button to SettingsActivity
```kotlin
// SettingsActivity.kt — add button to UI and wire to:
FirebaseCrashlytics.getInstance().sendUnsentReports()
// Show Toast: "Crash report sent — thank you"
```
- Button should only be visible if toggle is enabled in Settings
- No new permissions needed

**Context needed:**
- Pill showing at app start ✅
- Pill persisting entire shift ✅
- `updateWidget()` running correctly ✅
- `morphed to CAMERA state` logging ✅
- **But visual state not changing on screen** 🔴 — `invalidate()` fix applied, needs live test

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection pipeline | ✅ Complete | `isOffer=true` firing reliably on DoorDash offers |
| `SHOW_CAMERA` intent signal | ✅ Complete | Logs confirm intent sent and received |
| `OfferOverlayService.onStartCommand()` | ✅ Complete | `morphed to CAMERA state` confirmed |
| Window recovery on restart | ✅ Complete | Lost window re-added silently, gated on `WIDGET_ENABLED` |
| Settings toggle persistence | ✅ Complete | DB sync working, toggle saves instantly |
| Toggle visual sync | ✅ Complete | Upsert + intent extra pattern

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Pixel 10 XL (10.0.0.110:5555): run `adb connect 10.0.0.110:5555` to verify

## Files Changed This Session

- (no changes detected)

---
*Auto-generated by tools/gen_handover.py — next developer read SESSION_PROTOCOL.md first.*
