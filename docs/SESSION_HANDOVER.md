# GigLens — Session Handover
**Date:** 2026-06-09
**Version at session end:** 0.1.130
**Build state:** PASSING
**Authored by:** Claude (Anthropic) — manual handover

---

## Summary of Session

This session completed a major architectural shift: GigLens no longer depends on
MediaProjection/OCR for offer scoring. The accessibility tree is now the primary
data source. OCR remains as a fallback only if accessibility extraction fails.

---

## Changes This Session

### 1. `OfferDetectorService.kt` — Accessibility Extraction (PRIMARY)
**Why:** `ScreenCaptureService` was dying mid-shift due to MediaProjection token
expiry, causing `CAPTURE_DEAD` on every accessibility event and blocking all offer
detection for the rest of the shift.

**What changed:**
- Added `extractOfferFromNodes()` — walks accessibility node tree and extracts
  pay, distance, restaurant, deliverBy, and countdown directly from DoorDash nodes.
  Confirmed against real log data: `$3.55 guaranteed | 6.0 mi | deliver by 10:55 pm | pickup | mcdonald's | 19`
- Added `ACTION_OFFER_EXTRACTED` broadcast + extras constants (EXTRA_PAY,
  EXTRA_DISTANCE, EXTRA_RESTAURANT, EXTRA_DELIVER_BY, EXTRA_SOURCE)
- Removed hard gate on `ScreenCaptureService.isRunning` — accessibility extraction
  now runs regardless of MediaProjection state
- OCR fallback via `ACTION_OFFER_DETECTED` only fires if `ScreenCaptureService`
  is alive — prevents CAPTURE_DEAD spiral
- Registered `AccessibilityOfferReceiver` in `onServiceConnected()`, unregistered
  in `onDestroy()`

**Key CORRECT/WRONG:**
- CORRECT: accessibility extraction proceeds regardless of ScreenCaptureService state
- WRONG: hard gate on isRunning — blocks entire shift when MediaProjection token expires

---

### 2. `AccessibilityOfferReceiver.kt` — NEW FILE
**Why:** `ACTION_OFFER_EXTRACTED` broadcast had no receiver. Extracted offer data
was broadcasting into the void with nothing scoring it.

**What changed:**
- New `BroadcastReceiver` that listens for `ACTION_OFFER_EXTRACTED`
- Runs same scoring pipeline as `ScreenCaptureService.runOcr()` but without
  screenshot or OCR — uses pre-extracted pay/distance/restaurant directly
- Uses `goAsync()` — scoring involves DB + coroutine, exceeds 10s BroadcastReceiver limit
- Saves `OfferCapture` to DB with `screenshotPath=null`, `rawOcrText="source=accessibility"`
- Launches `OfferOverlayService` with full score result extras
- Registered in `AndroidManifest.xml` with `android:exported="false"`

**Validated:** Full pipeline fired on live offer — 63ms from detection to scored
pill on screen. pay=$2.59, distance=1.1mi, verdict displayed with color.

---

### 3. `OfferOverlayService.kt` — stopSelf() removal + CAPTURE_DEAD fix + countdown timer
**Why (stopSelf):** `stopSelf(startId)` was called after every `ACTION_SHOW_CAMERA`
and `ACTION_HIDE_CAMERA`. Service was killing itself after each offer, orphaning
the WindowManager view. Next offer restarted a fresh instance with `isViewAdded=false`,
pill disappeared and never recovered.

**Why (CAPTURE_DEAD):** Auto-trigger checked `ScreenCaptureService.isRunning` on
every null-action intent. Since ScreenCaptureService was always dead, every
accessibility event triggered CAPTURE_DEAD, blocking the overlay permanently.

**Why (countdown timer):** Pill was persisting verdict forever. Driver needs to
know how long they have to act and pill should reset after offer window closes.

**What changed:**
- Removed `stopSelf(startId)` from `ACTION_SHOW_CAMERA` and `ACTION_HIDE_CAMERA`
- Added duplicate `SHOW_CAMERA` guard — ignored if already in CAMERA state
- Removed auto CAPTURE_DEAD trigger from null-action else block
- Re-enabled `startRevertTimer()` — 30s countdown (capped at 30, reads
  `RESULT_DISPLAY_SECONDS` from DB via `revertDelaySeconds()`)
- `netLabelSpannable()` now shows `+$X.XX Ns` countdown on pill label
- Timer starts when result pill appears, ticks every second, reverts to IDLE at 0
- `verdict` reset to "UNKNOWN" on timer expiry so IDLE state is clean

---

## Road Laptop Setup (incomplete)
- Ubuntu 25.10 at 10.0.0.223, SSH accessible from milo-dev
- Installed: adb, git, python3, pip, build-essential, curl
- USB ADB not yet working — Pixel not detected in `lsusb`
- Likely cause: charge-only cable or USB mode not set to File Transfer on Pixel
- Next step: try different cable or confirm USB mode on Pixel notification shade

---

## Active Bug / Next Session Priority
- Wire `ACTION_OFFER_EXTRACTED` countdown seconds to overlay if needed
- Confirm 30s countdown visible on pill during real drive
- Resolve road laptop USB ADB (cable/mode issue)
- Consider removing MediaProjection entirely once accessibility path
  confirmed stable across full shift

---

## Devices & Build Environment
- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: check Wireless Debugging each session — port changes on reboot
- Samsung S20: 10.0.0.189:5555
- Road laptop: 10.0.0.223 (Ubuntu 25.10)

## Files Changed This Session
- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/AccessibilityOfferReceiver.kt (NEW)
- app/src/main/AndroidManifest.xml

---

## Features Left to Implement

### High Priority (affects daily driving)
1. Robolectric unit tests -- OfferScorer and AppConfigDao (tabled this session)
2. Partial Screen Sharing (Android 14+) -- app-specific capture instead of full screen (tabled this session)
3. Remove MediaProjection entirely -- ScreenCaptureService is dead weight now that accessibility path is stable. Needs one full confirmed shift first.
4. Road laptop USB ADB -- cable/mode issue, incomplete this session

### Medium Priority (improves driver experience)
5. Countdown sync to DoorDash timer -- currently starts at 30s on camera tap. Could sync to actual DoorDash offer countdown already extracted by accessibility (e.g. countdown=47)
6. Auto-capture mode clarification -- cachedAutoCapureMode supports "accessibility" and "both" but scorer fires before driver taps. Camera tap may no longer be needed in accessibility-only mode.
7. Skip PROCESSING state -- scorer already ran via AccessibilityOfferReceiver before driver taps. Pill could go straight from CAMERA to result, bypassing PROCESSING entirely.
8. Multi-offer handling -- verify second rapid offer correctly resets 30s timer

### Settings / Configuration
9. RESULT_DISPLAY_SECONDS UI -- key exists in DB but needs to be exposed in SettingsActivity so driver can adjust the 30s timer
10. Enabled platforms toggle -- cachedEnabledPlatforms supports multiple platforms but only DoorDash implemented
11. Auto-capture mode setting -- button / accessibility / both should be driver-configurable in Settings UI

### Data & Analytics
12. All offers history view -- driver cannot review past offers in UI
13. Daily/weekly earnings summary -- DailyNetValue table exists but no UI
14. Offer acceptance rate tracking -- no way to know which verdicts driver actually accepted

### Long-term Backlog
15. Driver License Scanner -- manual + IMAP triggers, pending_license_scans table
16. Document scanner -- llava:13b for insurance cards
17. Uber Eats / Grubhub support -- PlatformRegistry wired but no accessibility detection for other platforms
18. GigLens Play Store public release -- currently Internal Testing only

---

## UX Goal: One-Touch Start

**Desired behavior:**
- Driver opens GigLens
- Flips toggle ON
- Pill appears, app is ready
- No screenshare popup, no grant dialog, no extra taps

**Current broken behavior:**
- Toggle flip triggers MediaProjection grant dialog (extra tap)
- Driver must approve screenshare before app is functional
- On Android 16 this requires Play Store Internal Testing trust level

**Target architecture after MediaProjection removal:**
- Toggle ON → startService(OfferDetectorService) + startService(OfferOverlayService)
- Both services start silently, pill appears immediately
- Zero permission dialogs at runtime (Accessibility permission granted once in Settings)
