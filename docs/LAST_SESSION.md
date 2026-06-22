# GigLens — Latest Session Handover

**Most recent session:** 2026-06-22 → see [LAST_SESSION_20260622.md](LAST_SESSION_20260622.md)
**Previous session:** 2026-06-21 → see [HANDOVER_2026_06_21.md](HANDOVER_2026_06_21.md) (dedup full fix, town bearing overhaul, pill heading town, Play Store deploy v0.1.211)
**2026-06-20:** [LAST_SESSION_20260620.md](LAST_SESSION_20260620.md)
**2026-06-19:** [LAST_SESSION_20260619.md](LAST_SESSION_20260619.md)

---

# GigLens — Session Handover (2026-06-19 archive — see above for latest)
**Date:** 2026-06-19
**Version at session end:** 0.1.186 (multiple deploys via deploy.sh auto-commit)
**Build state:** PASSING (compileDebugKotlin + compileReleaseKotlin both clean)
**Conducted by:** Claude (Anthropic) — manual session, post-shift analysis + pin-detection design

---

## What Was Completed This Session

### 1. Post-shift DB analysis — town estimation accuracy diagnosed
- Pulled real offer_captures DB from Pixel via adb run-as + WAL files
- Two offers tonight: Chick-fil-A (id=1, 18:02, estimated Delran Township,
  actual Burlington NJ) and Mochiatsu (id=2, 18:50, estimated Burlington
  Township, actual Willingboro NJ)
- Confirmed real miss distance: ~3-4 miles (not 5 as initially estimated)
- Root cause of null pickupDistance/deliveryDistance columns confirmed:
  TownEstimate data class did not expose pickupLegMi/deliveryLegMi fields
  (computed correctly inside estimateTown() but never returned to caller),
  AND OfferCapture constructor in AccessibilityOfferReceiver never set
  these fields at all

### 2. Fixed TownEstimate to expose pickup/delivery leg distances
- Added pickupLegMi: Double = 0.0 and deliveryLegMi: Double = 0.0 fields
  to TownEstimate data class in DeliveryTownEstimator.kt
- Updated all return sites in estimateTown() to populate these fields
- Added logcat WARNING when confidence is medium/low (speed <= 2.0 m/s)
  logging actual speed, bearing, and leg distances for post-shift diagnosis
- Fixed AccessibilityOfferReceiver.kt OfferCapture constructor to set
  pickupDistance = townEstimate?.pickupLegMi and
  deliveryDistance = townEstimate?.deliveryLegMi (was previously setting
  deliveryDistance = raw total distance, which was wrong)
- Compiled and verified — next offer DB row will have real leg data

### 3. Designed and decided pin-detection town estimator algorithm
- Full decision documented in docs/DECISIONS.md (2026-06-19 entry)
- Algorithm: screenshot → HSV color filter for pins → pixel distance
  calibration using offer's stated total distance → compass bearing from
  pickup→dropoff pixel vector → project from restaurant GPS → reverse geocode
- KEY DECISIONS:
  * Pure Kotlin HSV color filtering (no OpenCV) as first attempt — zero
    new dependencies, zero APK size impact
  * Driver pin: solid saturated blue (confirmed from real screenshots —
    more distinctive than design doc suggested)
  * Briefcase/house pins: high-contrast white circles, very detectable
  * Map is always north-up (confirmed by Milo across all screenshots)
  * DoorDash total distance = full trip (driver→pickup→dropoff) — usable
    as self-calibrating miles/pixel anchor per offer
  * GPS-bearing estimator remains as fallback when fewer than 2 pins detected
  * OCR on map town labels REJECTED — labels are reference geography, not
    delivery town (Milo correction)
  * MediaProjection REJECTED — poor UX, already avoided project-wide
  * Color-based HSV for driver pin NOT rejected (earlier design doc was
    wrong about this — real screenshots show driver pin is clearly saturated
    blue, distinguishable from dark navy map background)
- OPEN RISKS documented: pin template stability across DoorDash updates,
  north-up assumption, restaurant geocoding accuracy
- Implementation files planned:
  * app/src/main/java/com/augusteenterprise/giglens/town/PinDetector.kt (NEW)
  * app/src/main/java/com/augusteenterprise/giglens/town/PinDetectionTownEstimator.kt (NEW)
  * DeliveryTownEstimator.kt (MODIFIED — dispatch logic)
  * AppConfig.kt (MODIFIED — new PIN_DETECTION_ENABLED key, already has
    SCREENSHOT_DELAY_MS from this session)

### 4. Real screenshots captured and analyzed (4 images)
- offer_screenshot_1781820126153.png: Chick-fil-A, 6:02 PM — route line
  visible, pins absent (screenshot fired before Mapbox finished rendering)
- offer_screenshot_1781823030325.png: Mochiatsu, 6:50 PM — same issue
- offer_screenshot_1781837795113.png: South Star Diner, 10:56 PM — ALL
  THREE PINS CLEARLY VISIBLE (briefcase at ~105,467; house at ~601,583;
  driver blue dot at ~117,577). This is the reference image for the
  pin-detection algorithm design.
- offer_screenshot_1781837808528.png: South Star Diner with notification
  overlay — pins still visible beneath overlay
- CONCLUSION: The first two screenshots confirm the rendering-timing bug.
  The last two confirm the algorithm is viable when screenshots are
  properly timed.

### 5. CV accuracy validation on South Star Diner screenshot
- Computed pixel vector pickup→dropoff: Δx=+496, Δy=+116 → bearing ≈ 103°
  (east-southeast)
- Computed delivery leg: 509 pixels × (6.1mi/619px) ≈ 5.01 miles
- Projected ~5mi east-southeast from South Star Diner (1655 NJ-38,
  Mount Holly) → lands in Hainesport/Eastampton area
- GPS-bearing estimator (current) would have used bearing=0° (north) if
  stationary at offer time → projects ~5mi north of Mount Holly into
  Burlington Township — exactly the class of miss experienced tonight
- CV approach would have been meaningfully more accurate on this offer

### 6. Added configurable screenshot delay (SCREENSHOT_DELAY_MS)
- New AppConfig key: SCREENSHOT_DELAY_MS, default "1500" (ms)
- OfferDetectorService now reads this from DB at runtime via coroutine,
  replaces hardcoded Handler.postDelayed(1500L) from prior session
- Exposed in SettingsActivity as a numeric field (0-5000ms range) with
  descriptive hint explaining what it does
- Purpose: give Mapbox tile rendering time to complete before screenshot
  fires — confirmed necessary from real shift screenshots
- New XML field added to activity_settings.xml (etScreenshotDelayMs)
- Note: res/layout backup files caused a mergeResources failure during
  this session — fixed by moving backups to /tmp/ instead of res/layout/.
  Going forward, ALL XML file backups must go to /tmp/, never to res/

### 7. Set up shift logging infrastructure
- Created ~/giglens/docs/shift_logs/ directory
- Added docs/shift_logs/ to .gitignore (runtime data, not source)
- tee-based logcat capture command for future shifts:
  adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) logcat -v threadtime |
  tee ~/giglens/docs/shift_logs/shift_$(date +%Y%m%d_%H%M%S).log |
  grep -E "OfferDetector|AccessibilityOfferReceiver|DebugOfferEmailer|
  testTakeScreenshot|OfferOverlayService|DeliveryTownEstimator"
- Single-command DB pull for post-shift analysis (all three WAL files):
  adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) shell run-as
  com.augusteenterprise.giglens cat databases/offer_database >
  /tmp/offer_database && adb ... cat databases/offer_database-wal >
  /tmp/offer_database-wal && adb ... cat databases/offer_database-shm >
  /tmp/offer_database-shm && cd /tmp && sqlite3 -header -column
  offer_database "SELECT restaurant, estimatedTown, pickupDistance,
  deliveryDistance, totalDistance, estimatedTownMethod FROM offer_captures
  ORDER BY timestamp DESC LIMIT 5;"

---

## What Was Left Incomplete

- PinDetector.kt — NOT STARTED. Algorithm fully designed and documented,
  implementation blocked on validating that the 1500ms screenshot delay
  fix actually produces fully-rendered pin screenshots on next shift.
  DO NOT implement until rendered screenshots are confirmed.
- PinDetectionTownEstimator.kt — NOT STARTED. Same blocker as above.
- Debug email pipeline (DebugOfferEmailer) — still unvalidated end-to-end.
  No email was received this shift. Root cause unknown — no logcat was
  running during the shift to capture what happened. Use tee-based logcat
  capture next shift (see above) to diagnose.
- testTakeScreenshot() SUCCESS still not confirmed — screenshots were
  captured (files exist on device) but images 1 and 2 show incomplete
  rendering. The 1500ms configurable delay fix addresses this but needs
  real-shift validation.
- Town accuracy cross-reference (Nominatim full address components) —
  designed, deferred until pin-detection is working
- Accessibility disclosure screen rebuild — untouched
- Phase 1 scoring redesign — untouched
- Registration + Stripe billing — untouched
- Sentiment Agent repo — untouched

---

## Known Broken (do not ignore)

- GPS-bearing town estimator still the active primary path — it produced
  wrong towns both offers tonight. This is a known architecture limitation
  (bearing unreliable at low speed). Fix is the CV pin-detection approach,
  which is designed but not yet built.
- Debug email not firing — unknown root cause. Must capture logcat next
  shift to diagnose. Check: (1) is testTakeScreenshot succeeding?
  (2) is DebugOfferEmailer.sendAsync being called? (3) are SMTP creds
  baked into BuildConfig correctly? All three need logcat confirmation.
- Location permission still needs re-granting after the uninstall from
  the prior session — re-grant before any town-estimation testing:
  Settings → Apps → GigLens → Permissions → Location → Allow
- Accessibility service toggle: must be toggled OFF then ON after any
  install that changes capability XML. If testTakeScreenshot keeps failing
  with SecurityException, this is the first thing to check.
- Pre-existing compiler warnings (not regressions):
  OfferDetectorService.kt: unused mode variable, multiple recycle()
  deprecated warnings
  SettingsActivity.kt: delicate API warnings (lines 42, 52, 107, 114,
  141, 157)
  AccessibilityOfferReceiver.kt: distance != null always true, Elvis
  operator warning
  None block the build.
- res/layout backup files: patch scripts must NEVER write backups to
  res/layout/ — Android resource merger rejects non-.xml files in that
  directory. Always use /tmp/ for XML file backups.

---

## Next Session — Start Here

1. BEFORE ANYTHING ELSE: run tee-based logcat capture command (see above)
   so nothing is lost from the next shift regardless of what happens.

2. On next shift or search-and-log-off test: trigger one real offer, then
   immediately pull screenshots:
   mkdir -p ~/giglens/docs/shift_logs/screenshots_$(date +%Y%m%d)
   adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) pull
   /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/
   ~/giglens/docs/shift_logs/screenshots_$(date +%Y%m%d)/

3. Upload the screenshot(s) here for visual inspection. Specifically
   checking: are all three pins (briefcase, house, driver blue dot)
   clearly visible and fully rendered? If yes → implement PinDetector.kt.
   If pins still absent → increase SCREENSHOT_DELAY_MS in Settings
   (try 2000ms) and retest before implementing.

4. Check logcat output for DebugOfferEmailer lines:
   "Debug offer email sent" → email pipeline working, check Gmail inbox
   "Debug offer email failed: ..." → SMTP issue, check credentials
   No line at all → sendAsync never called, check if result != null block
   is being reached in AccessibilityOfferReceiver

5. Once screenshots confirm full pin rendering: implement Piece 2
   (PinDetector.kt) — pure Kotlin HSV color filtering, zero new
   dependencies. See DECISIONS.md 2026-06-19 entry for full algorithm.
   Key thresholds from visual analysis of South Star Diner screenshot:
   - Briefcase/house pins: high brightness (V>200), low saturation (S<30)
   - Driver pin: saturated blue (H=200-240, S>150, V>100)
   - Blob size filter: 200-5000 pixels (eliminates noise and map features)

6. After PinDetector.kt is working: implement Piece 3
   (PinDetectionTownEstimator.kt) — uses PinDetector results to compute
   bearing and delivery leg, projects from restaurant GPS, reverse geocodes.
   Wire into existing DeliveryTownEstimator dispatch as primary path with
   GPS-bearing as fallback.

7. Pull DB after next offer with full leg data query:
   sqlite3 -header -column offer_database "SELECT restaurant, estimatedTown,
   pickupDistance, deliveryDistance, totalDistance, estimatedTownMethod
   FROM offer_captures ORDER BY timestamp DESC LIMIT 3;"
   Confirm pickupDistance and deliveryDistance are now populated (not null)
   — this validates the fix from this session.

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated live this session |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | pay/distance/restaurant all correct |
| Offer dedup guard | ✅ Working | Unaffected |
| GPS-bearing town estimation | ⚠️ Active but inaccurate | Produces adjacent-town misses at low speed — replacement in design |
| TownEstimate leg distance fields | ✅ Fixed | pickupLegMi/deliveryLegMi now returned and persisted |
| testTakeScreenshot() | 🔲 Fires, timing fix applied | 1500ms delay added — needs real-shift validation |
| Debug email pipeline | 🔲 Built, NOT validated | No email received this shift, no logcat to diagnose |
| Screenshot delay configurable | ✅ Built | SCREENSHOT_DELAY_MS in Settings, 0-5000ms range |
| PinDetector.kt | 🔲 NOT STARTED | Blocked on screenshot rendering validation |
| PinDetectionTownEstimator.kt | 🔲 NOT STARTED | Blocked on PinDetector.kt |
| Overlay pill stale-state fix | 🔲 Applied, not validated | sheetState reset on re-attach |
| AUTO_CAPTURE_MODE default | 🔲 Changed, not confirmed | "accessibility" default, needs fresh install check |
| Google Play deploy pipeline | ✅ Working | New service account working |
| Gitleaks staged scanning | ✅ Working | protect --staged, GCP rules added |
| GitHub Dependabot | ✅ Active | Weekly Gradle scan |
| Accessibility disclosure screen | 🔲 Not started | |
| Phase 1 scoring redesign | 🔲 Scoped | |
| Registration + Stripe billing | 🔲 Scoped | |
| Sentiment Agent repo | 🔲 Scoped | |

---

## Decisions Made This Session

1. Pin-detection approach chosen over GPS-bearing as primary town estimator
   — GPS bearing confirmed unreliable across multiple real shifts. Full
   decision with rationale and rejected alternatives in DECISIONS.md.

2. Pure Kotlin HSV color filtering chosen over OpenCV — zero dependency,
   zero APK size impact. OpenCV remains an option if HSV filtering proves
   insufficient after real testing.

3. Driver pin IS detectable by color — real screenshots show it as clearly
   saturated blue, contradicting the design doc's claim of "soft muted blue
   indistinguishable from water features." Design doc updated via DECISIONS.md.

4. North-up map assumption confirmed by Milo — load-bearing assumption for
   the CV algorithm. If DoorDash ever rotates the map, results will be wrong.
   Sanity checks (50-mile radius from driver) catch gross errors.

5. Screenshot delay made configurable (not hardcoded) — allows per-device
   tuning without rebuilding. Default 1500ms, range 0-5000ms in Settings.

6. XML backup files must never go to res/ directories — Android resource
   merger rejects non-.xml files. All XML backups go to /tmp/ going forward.
   This is now a standing rule.

7. Shift log directory (docs/shift_logs/) gitignored — runtime data, not
   source. Screenshots and DB pulls are debug artifacts, not project files.

---

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/geocoding/DeliveryTownEstimator.kt
  (TownEstimate extended with pickupLegMi/deliveryLegMi, all return sites
  updated, low-confidence bearing warning added)
- app/src/main/java/com/augusteenterprise/giglens/service/AccessibilityOfferReceiver.kt
  (OfferCapture constructor now sets pickupDistance/deliveryDistance from
  townEstimate fields instead of raw distance)
- app/src/main/java/com/augusteenterprise/giglens/data/AppConfig.kt
  (SCREENSHOT_DELAY_MS key added)
- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
  (screenshot delay now DB-driven via coroutine, not hardcoded 1500L)
- app/src/main/java/com/augusteenterprise/giglens/ui/SettingsActivity.kt
  (screenshotDelayMs load/save/UI wired)
- app/src/main/res/layout/activity_settings.xml
  (etScreenshotDelayMs field added before Save button)
- docs/DECISIONS.md (pin-detection algorithm decision added 2026-06-19)
- docs/LAST_SESSION.md (this file)
- docs/LAST_SESSION_20260619.md (dated permanent copy)
- .gitignore (docs/shift_logs/ added)

---

## Features Left to Implement (Priority Ranked)

1. Validate screenshot rendering (1500ms delay fix) — BLOCKS EVERYTHING below
2. Implement PinDetector.kt — blocked on #1
3. Implement PinDetectionTownEstimator.kt — blocked on #2
4. Debug email pipeline validation — diagnose via logcat next shift
5. Decide pill display (town vs profit-only) — still undecided
6. Decide AUTO_CAPTURE_MODE default for real drivers
7. Accessibility disclosure screen rebuild (ViewBinding)
8. Phase 1 scoring redesign (GREEN/YELLOW/RED)
9. Registration + Stripe billing
10. Sentiment Agent repo

---

*Next developer: read SESSION_PROTOCOL.md and WORKING_AGREEMENTS.md first,
then return here. KEY PRIORITY: do NOT start implementing PinDetector.kt
until real-shift screenshots confirm the 1500ms delay fix produces fully-
rendered pin images. The South Star Diner screenshot (offer_screenshot_
1781837795113.png) is the reference for what "correct" looks like —
all three pins visible, large, high contrast. Images 1 and 2 from tonight
show what "wrong" looks like — route line only, no pins. Implement only
after confirming the new delay fix produces images like #3, not like #1/2.*
