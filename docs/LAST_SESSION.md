# GigLens — Session Handover
**Date:** 2026-06-17
**Version at session end:** 0.1.154 (no version bump — no deploy this session, debug-only changes)
**Build state:** PASSING
**Conducted by:** Claude (Anthropic) — manual session

---

## What Was Completed This Session

### 1. Investigated v154 delivery town estimation results from real shift — FAILED, root cause found
- GPS-based bearing projection produced wildly wrong towns ("cities never heard of")
- Root cause: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION permission was DENIED
  on the Pixel after the debug APK reinstall wiped app data — confirmed via
  `dumpsys package` showing granted=false for both permissions
- Even when permission IS granted, GPS `bearing` field is unreliable at low/no
  speed (defaults to 0.0/north), causing confident-but-wrong projections
- DB query confirmed: only 1 row inserted despite 5 distinct offers detected in
  screen_texts.log (dedup guard IS working correctly), but estimatedTown was
  blank on that row (location permission issue, not a code bug)

### 2. Extensive investigation into delivery-town-via-map-data — CLOSED, documented
- Confirmed (again) that DoorDash's accessibility tree exposes zero map pin
  coordinates, zero content descriptions on map elements, zero street addresses
- Discussed and rejected: directional bearing from driver's map pin (not
  programmatically readable — confirmed dead end, do not revisit)
- Discussed and rejected: fixed pixel-to-mile conversion constant (zoom level
  varies per offer based on Mapbox auto-fit-bounds behavior — not reliable)
- IDENTIFIED VIABLE PATH: pixel-to-mile conversion IS solvable per-offer using
  the offer's own known total distance as a self-calibrating anchor (no fixed
  constant needed) — but requires a screenshot to measure pin pixel positions

### 3. Researched MediaProjection consent requirements — confirmed unavoidable for that API
- Verified via web search: Android 14+ requires fresh consent for every
  MediaProjection capture session, cannot be suppressed, always shows some
  system-level indicator (status bar icon minimum, often a notification)
- This applies regardless of whether capture is triggered by user tap or
  automatically — Android does not distinguish trigger method for this
  permission requirement
- CONCLUSION: MediaProjection itself is not viable given product preference to
  avoid the consent/notification UX

### 4. DISCOVERED VIABLE ALTERNATIVE: AccessibilityService.takeScreenshot()
- Available since Android 11 (API 30) — GigLens's OfferDetectorService is
  already an AccessibilityService, so this requires NO additional permission
  beyond what's already granted during onboarding
- Confirmed via research: captures full screen contents silently — no
  notification, no toast, no persistent indicator (unlike MediaProjection)
- This is the SAME API some stalkerware apps abuse for silent screen capture —
  worth being transparent about this use case in Play Store listing later
- minSdk is 26 (Android 8) — takeScreenshot() requires API 30, so a runtime
  version check is mandatory; gracefully no-ops on older devices

### 5. Built test implementation of takeScreenshot() — gated correctly, NOT YET VALIDATED
- File: OfferDetectorService.kt
- New function: testTakeScreenshot() — temporary/throwaway, meant to be
  replaced once confirmed working
- Gated INSIDE the existing `if (looksLikeOfferScreen(rootNode))` confirmed-new-
  offer block — physically cannot fire on any screen other than a confirmed
  DoorDash offer screen (per explicit requirement this session)
- Gated by `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` (API 30) check
- Saves resulting bitmap as PNG to app's debug folder for visual inspection
- STATUS: code compiles, debug APK built, but NOT YET TESTED on a real offer —
  no shift happened this session to trigger a live offer screen

### 6. Fixed multiple `\$` string-escaping bugs from prior sessions' Python patch scripts
- Recurring bug pattern: heredoc Python scripts escaping `$` as `\$` when writing
  Kotlin string templates — produces literal backslash-dollar in the .kt file,
  which is valid Kotlin syntax (escaped dollar sign) but semantically wrong
  (prints literal "$variablename" instead of interpolating the actual value)
- Fixed in: testTakeScreenshot() logging (7 instances), signalCapture() dedup
  fingerprint logging (3 instances, left over from a previous session's patch)
- NOT touched: line 440 `Regex("""\$(\d{1,3}\.\d{2})""")` — this is CORRECT,
  intentionally escapes a literal $ for regex matching, not a bug

### 7. MAJOR BUG HUNT: diagnosed and fixed a build-blocking issue unrelated to this
   session's actual work
- Discovered two completely untracked, uncommitted files from an unknown prior
  session (likely DeepSeek/Project Autonomous-generated):
  AccessibilityDisclosureScreen.kt and AccessibilityDisclosureActivity.kt
- These used Jetpack Compose, but Compose was NEVER configured in this project's
  build.gradle.kts (confirmed: zero "compose" references anywhere in the
  Gradle config — GigLens is 100% ViewBinding/XML)
- This caused a confusing cascading compile failure (`@error.NonExistentClass`
  on a DIFFERENT, unrelated file) that persisted through clean builds, daemon
  restarts, and full Gradle cache wipes — took significant diagnostic time to
  trace back to these two orphaned files
- RESOLVED: deleted both files. Build is now clean.
- The underlying idea (a disclosure screen explaining accessibility permission
  usage before requesting it) was good and well-written — scoped for proper
  rebuild using ViewBinding in FEATURE_BACKLOG.md, content preserved there for
  reference

---

## What Was Left Incomplete

- testTakeScreenshot() built but NOT validated on a real device/real offer —
  needs a live shift to trigger an actual DoorDash offer screen and confirm:
  (a) the screenshot succeeds without error, (b) the saved PNG actually shows
  the DoorDash map clearly, (c) no unexpected permission dialog appears
- OpenCV pin-detection pipeline — not started, blocked on #1 above being
  validated first
- Pixel-to-mile self-calibration math (using known offer distance as anchor) —
  designed conceptually, not yet implemented in code
- Delivery town estimation via GPS bearing — still broken in v154 (permission
  was denied during last shift), need driver to manually re-grant location
  permission before next shift to get a fair test of whether the ORIGINAL
  bearing approach works at all when permission is properly granted (separate
  question from whether bearing is reliable at low speed, which is a known
  limitation regardless)
- Accessibility disclosure screen rebuild (ViewBinding version) — scoped only

---

## Known Broken (do not ignore)

- Location permission gets reset to denied whenever the debug APK is
  uninstalled/reinstalled (expected Android behavior, not a bug, but it means
  EVERY TIME we reinstall debug APK, must manually re-grant location permission
  before testing town estimation, or it will silently fail with blank fields
- GPS `bearing` field defaults to 0.0 (interpreted as "due north") when stale
  or unavailable — this is a real limitation of the original v154 approach,
  separate from the permission issue. Confirmed via real shift data: produces
  confidently wrong town guesses. DO NOT re-enable the v154 bearing-based pill
  display without first solving this — currently the pill estimate is
  unreliable. Recommend reverting pill to net-profit-only display until either
  (a) takeScreenshot()-based pin detection is validated, or (b) bearing
  reliability is independently solved.
- Two confirmed dead-end investigations (do not re-investigate):
  1. Map content descriptions / accessibility tree coordinate exposure — DoorDash
     exposes nothing. Closed permanently across two separate sessions of testing.
  2. Idle-screen zone text ("nj: moorestown/mt. laurel") — confirmed to be
     driver's assigned zone, not delivery zone, not useful for this purpose.

---

## Next Session — Start Here

1. **CRITICAL FIRST STEP**: before any further town estimation work, manually
   re-grant location permission on the Pixel (Settings → Apps → GigLens →
   Permissions → Location → Allow) since the debug APK reinstall this session
   reset it to denied
2. On next real shift: trigger at least one real offer to test
   testTakeScreenshot() — pull the saved PNG afterward via:
adb -s 10.0.0.110:<port> pull /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/ ~/giglens/docs/screenshot_test/
Visually inspect the PNG — does it clearly show the DoorDash map, pins, and
   route line? Any visual artifacts, blank/black image, or errors in logcat?
3. If screenshot test succeeds: scope and build the OpenCV pin-detection +
   self-calibrating pixel-to-mile pipeline as a proper Phase 2 feature
4. If screenshot test fails: town estimation is likely a dead end entirely —
   revert pill to net-profit-only, remove delivery town estimation code/UI,
   keep accuracy-tracking DB columns dormant in case revisited later
5. Decide: should the v154 bearing-based estimate be temporarily disabled in
   the pill (reverted to profit-only) RIGHT NOW, given it's been shown to
   produce wrong results, even before the screenshot approach is validated?
   This wasn't decided definitively this session — worth a clear yes/no answer
   to start next session.
6. Rebuild accessibility disclosure screen using ViewBinding (see
   FEATURE_BACKLOG.md for full content/scope — was deleted this session due to
   being built with unconfigured Compose framework)
7. Continue with previously-scoped work: Phase 1 scoring redesign (GREEN/
   YELLOW/RED), Registration + Stripe billing, Sentiment Agent repo

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated on real shift |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | |
| Offer dedup guard (receiver-level) | ✅ Confirmed working | Real shift data: 5 offers seen, 1 correctly inserted |
| Delivery town estimation (GPS bearing) | ❌ BROKEN | Confirmed wrong on real shift — see Known Broken |
| AccessibilityService.takeScreenshot() test | 🔲 Built, unvalidated | Needs real shift to test |
| Town accuracy tracking infrastructure | ✅ Built | DB columns + notification system intact, dormant pending decision |
| MediaProjection dialog suppression | ✅ Fixed | No dialog for accessibility/tap modes |
| Tap-to-capture mode | ✅ Working | |
| LIVE badge in accessibility mode | ✅ Fixed | |
| Accessibility disclosure screen | 🔲 Deleted, rescoped | Was broken Compose code, rebuild planned with ViewBinding |
| Scoring redesign (Phase 1) | 🔲 Scoped | Not started |
| Registration + Stripe billing | 🔲 Scoped | Not started |
| Sentiment Agent (separate repo) | 🔲 Scoped | Not started |
| Order archive (day/week/month) | 🔲 Not started | |
| ScreenCaptureService removal | 🔲 Planned | |
| Play Store review submission | 🔲 Pending | |

---

## Decisions Made This Session

1. MediaProjection confirmed unusable given product requirement to avoid
   consent dialogs/persistent notifications — Android provides no way around
   this for that specific API, confirmed via research, regardless of trigger
   method (tap vs automatic).
2. AccessibilityService.takeScreenshot() identified and adopted as the
   replacement approach — no MediaProjection needed, since GigLens already has
   accessibility service permission. This is a meaningfully better path than
   what was being pursued earlier in the session.
3. Pixel-to-mile conversion for map-based pin distance CAN be solved without a
   fixed constant, using the offer's own known total distance as a per-offer
   calibration anchor — conceptually sound, not yet implemented.
4. On-device computer vision (OpenCV bundled in APK) confirmed as the right
   approach for pin detection — no cloud vision API, no recurring cost, no data
   leaving the device. Matches existing project philosophy (local Ollama,
   self-hosted DB, etc.)
5. Two Compose-based files from an unknown prior session were deleted after
   being identified as the root cause of a confusing, persistent build failure.
   The underlying disclosure-screen concept was good and is preserved in the
   backlog for a proper ViewBinding rebuild.
6. NOT YET DECIDED: whether to immediately revert the pill's delivery-town
   display back to profit-only given confirmed inaccuracy, or leave it as-is
   while the screenshot-based approach is being validated. Flagged explicitly
   for next session's first decision.

---

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: IP 10.0.0.110, port saved in ~/giglens/.pixel_port (was 41149
  this session, changes on reboot — deploy.sh handles reconnection automatically)
- Location permission on Pixel: CURRENTLY DENIED as of session end — must
  manually re-grant before any town-estimation testing
- Samsung S20 (10.0.0.189:5555): available as alternate test device, not used
  this session
- Keystore: ~/giglens/giglens-release.keystore
- Play Store JSON key: ~/giglens/google-play-api-key.json (DO NOT COMMIT)

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
  (testTakeScreenshot() added, dollar-escaping bugs fixed)
- app/src/main/java/com/augusteenterprise/giglens/ui/AccessibilityDisclosureScreen.kt
  (DELETED — was broken, uncommitted, Compose-based, never wired up)
- app/src/main/java/com/augusteenterprise/giglens/ui/AccessibilityDisclosureActivity.kt
  (DELETED — same as above)
- docs/FEATURE_BACKLOG.md (added disclosure screen rebuild scope)

No deploy.sh run this session — all changes are debug-build-only pending real
offer testing. No version bump, no Play Store upload.

---

## Features Left to Implement (Priority Ranked)

See docs/FEATURE_BACKLOG.md for full list. Top of stack as of this session end:

1. **Validate testTakeScreenshot()** on next real shift — blocking everything else town-related
2. **Decide pill display** — revert to profit-only now, or wait for screenshot validation?
3. **Re-grant location permission** on Pixel before next test
4. **Accessibility disclosure screen** — rebuild with ViewBinding
5. **Scoring redesign (Phase 1)** — still queued, untouched this session
6. **Registration + Stripe billing** — still queued, untouched this session
7. **Sentiment Agent repo** — still queued, untouched this session

---

*Next developer: read SESSION_PROTOCOL.md first, then return here. PRIORITY:
re-grant location permission before testing anything town-related — see Known
Broken section.*

---

## Addendum — GPS Permission Toggle Fix (built, not yet validated)

### What was built
SettingsActivity.kt: switchGps toggle now actually requests Android location
permission (ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION) when turned ON,
instead of silently just setting a DB flag with no real permission behind it.
Mirrors the existing switchWidget/OverlayPermissionHelper pattern already used
in this file.

### Why this was needed
Confirmed bug from 2026-06-17 shift: GPS_ENABLED flag could be "true" in the DB
while actual Android location permission was denied, causing
LocationHelper.getCurrentLocation() to silently return null with zero warning
to the driver — this is what caused delivery town estimation to fail silently
that shift.

### New behavior
- Toggle ON + permission already granted → DB flag set true immediately
- Toggle ON + permission not granted → toggle snaps back to OFF, real Android
  permission dialog launches via locationPermissionLauncher
- Permission granted in dialog → toggle flips back to ON, DB flag set true
- Permission denied in dialog → toggle stays OFF, Toast shown explaining why,
  DB flag set false

### NOT YET VALIDATED
Built and compiles cleanly (debug APK built successfully), but could not be
sideloaded/tested this session — developer was remote, away from home network,
saved Pixel port (41149) unreachable. 

### Next session — test this first
1. Connect to Pixel (port may have changed again — check Wireless Debugging)
2. Sideload latest debug APK
3. In Settings, toggle GPS off then on — confirm REAL Android permission
   dialog appears (not silent flag flip)
4. Deny once to confirm toggle correctly snaps back to OFF with Toast message
5. Toggle on again, grant permission, confirm toggle stays ON and DB reflects
   "true"
6. THEN proceed with re-testing delivery town estimation / testTakeScreenshot()
   from previous session, now that there's a reliable way to ensure location
   permission is actually granted before testing
