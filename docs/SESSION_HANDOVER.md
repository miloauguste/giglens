# GigLens — Session Handover
**Date:** 2026-06-07
**Version at session end:** 0.1.115
**Build state:** PASSING
**Conducted by:** Claude (Anthropic) — manual override

---

## What Was Completed This Session

- ✅ **Camera pill now fires on live DoorDash offers — end-to-end pipeline working**
  - Root cause: `CAPTURE_COOLDOWN_MS` early return blocked fingerprint check before `SHOW_CAMERA` fired
  - Fix: moved cooldown gate to after `SHOW_CAMERA` send, only throttles `signalCapture()`
  - Logs confirmed: `NEW offer screen detected`, `Offer screen confirmed — sending SHOW_CAMERA`, `Widget morphed to CAMERA state`

- ✅ **OfferOverlayService overlay window recovery on process restart**
  - Root cause: `START_STICKY` reused service instance without calling `onCreate()`, overlay window lost after process death
  - Fix: window validity check in `onStartCommand()` — re-adds silently if lost
  - Gated on `WIDGET_ENABLED` DB check — pill only re-appears if toggle is on

- ✅ **Settings toggle now saves correctly and syncs with MainActivity**
  - Root cause: `SettingsActivity` wrote to DB, `MainActivity.onResume()` read from `SharedPreferences`
  - Fix: `MainActivity.onResume()` now reads `WIDGET_ENABLED` from DB via `lifecycleScope.launch`
  - Single source of truth — no Save button required, saves instantly on flip

- ✅ **`startForegroundService()` fix for Android 16**
  - Root cause: `MainActivity.onResume()` called `startService()` on Android 12+ — OS killed service before `onStartCommand` fired
  - Fix: unified to always use `startForegroundService()` on Android O+ (SDK 26+)

- ✅ **Permissions verified and working**
  - `SYSTEM_ALERT_WINDOW: allow`, `PROJECT_MEDIA: allow`, `captureRunning=true` steady

## What Was Left Incomplete

- ⏳ **Camera pill visual state not yet confirmed on screen** — logs show `morphed to CAMERA state` but driver did not see 📷 during test
- ⏳ **Auto-shutdown when DoorDash closes** — designed but not implemented
- ⏳ **Settings UI for gas price, MPG, wear & tear** — blocked until camera confirmed working

## Known Broken (do not ignore)

- 🔴 **Camera pill state change not rendering on screen**
  - `updateWidget rendering state=CAMERA` logs correctly
  - `windowManager.updateViewLayout()` runs without exception
  - Visual state not changing — likely GL/hardware layer invalidation issue
  - Fix to try: `rootView.invalidate()` after `updateViewLayout()`

- 🔴 **Firebase Crashlytics session not loading after shift**
  - Crashlytics console shows no sessions after shift ends
  - Likely cause: Crashlytics requires app force-close to flush upload buffer
  - Non-fatal events and breadcrumbs are logged in code but not appearing in console
  - Fix to try: call `FirebaseAnalytics.logEvent()` on offer detection to confirm
    Analytics pipeline is alive independently of Crashlytics
  - Alternative: add explicit `FirebaseCrashlytics.getInstance().sendUnsentReports()`
    call when shift ends (hook into auto-shutdown when DoorDash closes)

## Next Session — Start Here

**First task:** Fix camera pill visual rendering
```bash
# In OfferOverlayService.kt — end of updateWidget(), after windowManager.updateViewLayout()
rootView!!.invalidate()
rootView!!.requestLayout()
```

**Second task:** Fix Firebase Crashlytics not uploading after shift
```kotlin
// Add to OfferDetectorService.kt when isOffer=true fires:
FirebaseCrashlytics.getInstance().log("isOffer=true detected")
FirebaseCrashlytics.getInstance().sendUnsentReports()
```
Then check: https://console.firebase.google.com/project/giglens-57f0c/crashlytics

**Third task:** Implement auto-shutdown when DoorDash closes
- 60-second `Handler.postDelayed()` countdown when DoorDash package leaves foreground
- Cancel countdown if DoorDash returns within 60s
- On expiry: stop `ScreenCaptureService`, stop `OfferOverlayService`, write `WIDGET_ENABLED=false` to DB
- Show countdown on pill: "⏱ 60s" ticking down so driver knows shutdown is pending
- Hook `sendUnsentReports()` into shutdown sequence to flush Crashlytics before services stop

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection pipeline | ✅ Complete | `isOffer=true` firing reliably |
| `SHOW_CAMERA` intent signal | ✅ Complete | Sent and received confirmed in logs |
| `OfferOverlayService.onStartCommand()` | ✅ Complete | `morphed to CAMERA state` confirmed |
| Window recovery on restart | ✅ Complete | Lost window re-added silently |
| Settings toggle persistence | ✅ Complete | DB sync working |
| Camera pill visual rendering | 🔴 Broken | State changes in code, not on screen |
| Firebase Crashlytics upload | 🔴 Broken | Sessions not appearing after shift |
| Auto-shutdown on DoorDash close | 🟡 Pending | Design complete, not yet built |
| Offer history screen | 🟡 Pending | Data in DB, RecyclerView needed |
| Settings UI gas/MPG/wear | 🟡 Pending | Build after camera confirmed |

**Fourth task:** Add "Send Crash Report" button to SettingsActivity
```kotlin
// SettingsActivity.kt — add button to UI and wire to:
FirebaseCrashlytics.getInstance().sendUnsentReports()
// Show Toast: "Crash report sent — thank you"
```
- Button should only be visible if toggle is enabled in Settings
- No new permissions needed

## Tabled for Next Session — Automated Android Testing

**Concept:** Add unit and UI tests to catch regressions automatically
- **Robolectric** (highest value, no device needed):
  - `OfferScorer` v4 math — pay/mile/wear calculations
  - `buildOfferFingerprint()` — dedup logic
  - DB operations — toggle save/load, upsert behavior
- **Espresso** (device required):
  - Toggle on/off → pill appears/disappears
  - Settings save → DB sync verified
- **Start with:** Robolectric unit tests for `OfferScorer` and `AppConfigDao`
- **File to create:** `app/src/test/java/com/augusteenterprise/giglens/OfferScorerTest.kt`

## Tabled for Next Session — Automated Android Testing

**Concept:** Add unit and UI tests to catch regressions automatically
- **Robolectric** (highest value, no device needed):
  - `OfferScorer` v4 math — pay/mile/wear calculations
  - `buildOfferFingerprint()` — dedup logic
  - DB operations — toggle save/load, upsert behavior
- **Espresso** (device required):
  - Toggle on/off → pill appears/disappears
  - Settings save → DB sync verified
- **Start with:** Robolectric unit tests for `OfferScorer` and `AppConfigDao`
- **File to create:** `app/src/test/java/com/augusteenterprise/giglens/OfferScorerTest.kt`

## Tabled for Next Session — Partial Screen Sharing (Android 14+)

**Concept:** Switch MediaProjection from full-screen capture to app-specific capture (DoorDash only)
- Android 14+ supports `MediaProjectionManager` with per-app window selection
- Driver grants "share DoorDash only" — GigLens never sees other apps
- Token tied to DoorDash window — may be more stable, less likely to expire mid-shift
- Eliminates privacy concern — no banking apps, messages, etc. visible to GigLens
- Fallback: full screen share for Android 13 (S20)
- **Research first:** confirm Android 14 partial capture API is compatible with current `createScreenCaptureIntent()` flow
- **File to modify:** `ScreenCaptureService.kt` — `startCapture()` and `createDisplayResources()`

## Decisions Made This Session

- **Source of truth for widget toggle:** `AppConfigKeys.WIDGET_ENABLED` in DB — all code reads from DB
- **Window recovery:** detect and re-add lost window in `onStartCommand()` — no service restart, no flicker
- **Auto-shutdown design:** 60-second countdown when DoorDash closes, flush Crashlytics on shutdown

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: port 43013 this session (changes on reboot — check Wireless Debugging)
- Firebase: https://console.firebase.google.com/project/giglens-57f0c/crashlytics

## Files Changed This Session

- `OfferDetectorService.kt` — moved `CAPTURE_COOLDOWN_MS` check after `SHOW_CAMERA` send
- `OfferOverlayService.kt` — window recovery in `onStartCommand()`, gated on `WIDGET_ENABLED`
- `MainActivity.kt` — `onResume()` reads `WIDGET_ENABLED` from DB, `startForegroundService()` fix

---
