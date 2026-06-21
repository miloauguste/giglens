# GigLens — Session Handover
**Date:** 2026-06-20
**Version at session end:** 0.1.205 (versionCode 205) — final deploy was dark mode
**Pin-detection deploy:** v0.1.194 (first functional build with PinDetector live)
**Build state:** PASSING — compileDebugKotlin + compileReleaseKotlin clean
**Conducted by:** Claude (Anthropic) — implementation sprint: pin-detection, Settings UI overhaul, dark mode, dedup partial fix

---

## What Was Completed This Session

### 1. PinDetector.kt — Pure Kotlin HSV Pin Detection (NEW FILE)

Implemented `app/src/main/java/com/augusteenterprise/giglens/town/PinDetector.kt`.

This was the primary deliverable of this session — converting the algorithm design from the June 19 session into working code. The June 19 session had produced a complete design (South Star Diner screenshot validated, thresholds confirmed, blob-detection approach chosen) but explicitly blocked implementation until screenshot rendering was confirmed. Screenshots from the June 19 shift confirmed the 1500ms delay fix produces fully-rendered pins.

**Algorithm:**
1. Bulk-read all pixels via `bitmap.getPixels()` — avoids ~288K individual JNI calls on a 1080×2400 screenshot
2. Sample every 3rd pixel (STEP=3) — reduces work by 9× without losing blob shape
3. HSV threshold pass:
   - Blue (driver dot): H=200–240°, S>150/255, V>100/255
   - White (delivery pins): V>200/255, S<30/255
4. Connected-components flood fill on sampled grid points → blobs
5. Filter blobs by size (200–5000 original px, scales to ~22–555 sampled px at STEP=3)
6. Driver dot = largest qualifying blue blob
7. Sort white blob centroids by pixel distance from driver dot → closest = briefcase (pickup), farthest = house (dropoff)

**Key implementation details:**
- `gridKey(gx, gy): Long = gx.toLong() shl 20 or gy.toLong()` — uses shl 20 (not shl 16) to support grid coords up to ~1M; shl 16 breaks on 4K screens
- `@Volatile var latestResult` — ensures write from takeScreenshot executor thread is visible to coroutine reading it in estimateTown()
- Returns `PinDetectionResult(success=false)` when driver dot absent or no white blobs — never throws, always returns a usable object
- When only 1 white blob found: treated as briefcase only (closest = farthest = same blob), returns success=true with empty housePins list

**Files created:**
- `app/src/main/java/com/augusteenterprise/giglens/town/PinDetector.kt`

---

### 2. PinDetectionTownEstimator.kt — CV-Based Town Estimation (NEW FILE)

Implemented `app/src/main/java/com/augusteenterprise/giglens/town/PinDetectionTownEstimator.kt`.

Takes a `PinDetectionResult` (from PinDetector) + total distance (from the DoorDash offer text) + restaurant lat/lng (from Nominatim geocode) and produces a `TownEstimate`.

**Algorithm:**
1. **Pixel calibration:** `milesPerPixel = totalDistanceMi / (driverToPickupPx + pickupToDropoffPx)` — the DoorDash total distance is the full trip (driver→pickup→dropoff), so the sum of both pixel legs self-calibrates the scale
2. **Delivery leg:** `deliveryMi = pickupToDropoffPx × milesPerPixel`
3. **Bearing:** `atan2(dx, -dy)` where dx = dropoff.x − pickup.x, dy = dropoff.y − pickup.y — negate dy because screen y increases downward but geographic bearing increases northward; map is north-up (confirmed by Milo on all screenshots)
4. **Project:** spherical Earth forward projection from restaurant GPS at computed bearing for delivery miles
5. **Reverse geocode:** Nominatim `/reverse?zoom=10` → city > town > village > suburb > county priority chain

**Confidence:** always "high" when pin_detection path is taken — pixel bearing is derived from the actual map geometry, not the driver's GPS sensor.

**Files created:**
- `app/src/main/java/com/augusteenterprise/giglens/town/PinDetectionTownEstimator.kt`

---

### 3. PIN_DETECTION_ENABLED Config Key + seedIfAbsent() (AppConfig.kt + AppConfigDao.kt)

**AppConfig.kt changes:**
- Added `PIN_DETECTION_ENABLED = "pin_detection_enabled"` to `AppConfigKeys`
- Added entry to `defaultAppConfig()`: `AppConfig(AppConfigKeys.PIN_DETECTION_ENABLED, "true", "Use map pin pixel detection for town estimate: true | false")`

**AppConfigDao.kt changes:**
- Added `seedIfAbsent(key, value, description)` — uses `INSERT OR IGNORE` SQL; safe to call on every startup without overwriting user-changed values. This is the correct pattern for adding new config keys to existing installs without forcing a DB wipe.

**OfferDetectorService.onServiceConnected() change:**
- Seeds `PIN_DETECTION_ENABLED` via `seedIfAbsent()` from the config launch coroutine, so existing installs that upgrade from a build without this key get it automatically on first launch.

---

### 4. PinDetector Wired into OfferDetectorService + DeliveryTownEstimator Dispatch

**OfferDetectorService.testTakeScreenshot() — onSuccess block:**
- After saving the screenshot to disk, immediately calls `PinDetector.detect(softwareBitmap)` and logs result to logcat + Crashlytics
- `PinDetector.latestResult` is set as a side effect of `detect()`, making the result available to `DeliveryTownEstimator` when it runs for the next offer

**DeliveryTownEstimator.estimateTown() — dispatch change:**
- After geocoding the restaurant to get `restaurantCoords`, reads `PIN_DETECTION_ENABLED` from DB
- If enabled and `PinDetector.latestResult?.success == true`: calls `PinDetectionTownEstimator.estimate()` and returns (primary path)
- Clears `PinDetector.latestResult = null` after consuming to prevent stale result from prior offer bleeding into next offer
- Falls through to existing GPS-bearing path if disabled or no successful pin result

**Note (fixed in June 21 session):** The stale-result clear was originally inside the `success == true` branch only, meaning a `success=false` result would leave `latestResult` set and allow it to bleed into the next offer. Fixed June 21 by moving the clear to before the branch.

---

### 5. Pill Revert Timer Bug Fix (OfferOverlayService.kt)

The pill's auto-revert timer was not honoring the user-configured value in Settings. `revertDelaySeconds()` read the DB value correctly but the result was then clipped with `.coerceAtMost(30)` — a silent hard cap that overrode any value above 30 seconds regardless of what the driver set.

**Fix:** Changed `coerceAtMost(30)` to `coerceIn(5, 300)` — now respects the full 5–300 second range that the Settings UI claims to offer.

---

### 6. Map Pin Detection Toggle in Settings UI (SettingsActivity.kt + activity_settings.xml)

Added a CAPTURE SETTINGS section to `activity_settings.xml` containing `switchPinDetectionEnabled` (SwitchMaterial). `SettingsActivity.kt` loads the current value from DB on `onCreate()` and saves changes immediately on toggle (no Save button — same pattern as dark mode toggle).

This gives drivers a visible on/off control for pin detection without needing to clear app data or reinstall.

---

### 7. Settings Page Complete Redesign (activity_settings.xml — multiple passes)

The Settings page previously had no consistent design language. Fully redesigned to match `activity_main.xml`:
- `#F2F4F7` scrollable background
- `CardView` (not MaterialCardView) per section — 16dp corner radius, 0dp elevation, 14dp margins
- Section headers: 11sp bold, letterSpacing=0.08, `#6B7280`
- Row labels: 14sp bold, `#1A1A2E`
- Subtitles/descriptions: 11sp, `#9CA3AF`
- 1dp `#E5E7EB` dividers between rows
- 34dp square emoji icon placeholders (alpha=0.12) on switch rows

**Three text-contrast passes were required:**
1. First pass (replace_all on TextView textColor): invisible on RadioButton/CheckBox/TextInputEditText because those widgets don't inherit from plain TextView theme chain
2. Second pass: explicit `android:textColor="#1A1A2E"` on each TextInputEditText, RadioButton, enabled CheckBox individually + `android:textColorHint="#555555"` on TextInputLayout
3. Third pass: `app:boxStrokeColor="#1A1A2E"`, `app:boxStrokeWidth="1dp"`, `app:boxStrokeWidthFocused="2dp"` on every TextInputLayout — OutlinedBox default stroke uses `?attr/colorOnSurface` at reduced opacity, nearly invisible on white cards

**All view IDs preserved:** `switchGps`, `tvDetectedRegion`, `tilRegion`/`etRegion`, `tilCostPerMile`/`etCostPerMile`, `tilHourlyRate`/`etHourlyRate`, `btnLookupMpg`, `tilMpg`/`etMpg`, `tilGasPrice`/`etGasPrice`, `rgDataSharing`/`rbSharingNone`/`rbSharingAggregate`/`rbSharingIndividual`, `rgCaptureMode`/`rbCaptureOff`/`rbCaptureAccessibility`/`rbCaptureButton`/`rbCaptureBoth`, `tvAccessibilityPermStatus`, `tvAccessibilityActive`, `btnGrantAccessibility`, `cbPlatformDoordash`/`cbPlatformUbereats`/`cbPlatformGrubhub`/`cbPlatformInstacart`, `switchWidget`, `tvWidgetPermStatus`, `btnGrantOverlay`, `tilResultDuration`/`etResultDuration`, `etScreenshotDelayMs`, `switchPinDetectionEnabled`, `btnSaveSettings`

---

### 8. System-Wide Dark Mode (persistent, survives app restart)

**Architecture:**
- `AppCompatDelegate.MODE_NIGHT_YES/NO` for system-wide application
- Persistence via `SharedPreferences("giglens_prefs", key="dark_mode")` — synchronous, available in `Application.onCreate()` before any Activity
- `GigLensApp.onCreate()`: reads pref and calls `AppCompatDelegate.setDefaultNightMode()` as the very first thing — guarantees first frame renders correctly in the right mode
- `SettingsActivity`: `loadDarkModeToggle()` sets `isChecked` before attaching listener — prevents listener from firing during init and triggering Activity recreate loop

**Color token system:**
- `res/values/colors.xml` — 10 light-mode tokens (`gl_bg_page`, `gl_bg_card`, `gl_bg_header`, `gl_text_primary`, `gl_text_secondary`, `gl_text_muted`, `gl_text_chevron`, `gl_text_hint`, `gl_divider`, `gl_stroke`)
- `res/values-night/colors.xml` — same 10 tokens with dark values (`#121212` page, `#1E1E2E` cards, `#E8E8F0` primary text, `#2A2A3A` dividers, `#5A5A7A` strokes, etc.)
- `activity_main.xml` + `activity_settings.xml`: all hardcoded hex replaced with `@color/gl_*` references

**Special case preserved:** Earnings tile glass effect in `activity_main.xml` uses `android:background="#FFFFFF"` with `android:alpha="0.05"` on a `#1A1A2E` parent — these are intentional semi-transparent white overlays that must NOT become `@color/gl_bg_card` (they would turn dark in dark mode and break the glass-tile effect).

**New UI element added:** `switchDarkMode` (SwitchMaterial) added to activity_settings.xml in a new APPEARANCE section at the top of the Settings scroll.

**Constants added to GigLensApp:**
```kotlin
const val PREFS_NAME = "giglens_prefs"
const val PREF_DARK_MODE = "dark_mode"
```

---

### 9. Dedup Window Partial Fix — AccessibilityOfferReceiver (INCOMPLETE)

`AccessibilityOfferReceiver.kt` line 85: `DEDUP_WINDOW_MS` changed from `30_000L` to `120_000L` (2 minutes).

**Context:** Shak's Chicken & Gyro was captured twice — 12:05 AM and 12:06 AM, 60 seconds apart — slipping through the 30-second window. The fix was started but the session ended mid-task.

**What was NOT done (completed June 21):**
- `OfferDetectorService.kt` line 51: `OFFER_DEDUP_WINDOW_MS` still `30_000L` at session end — required a separate fix in the June 21 session

---

## What Was Left Incomplete

### Dedup fix in OfferDetectorService
`OFFER_DEDUP_WINDOW_MS = 30_000L` at `OfferDetectorService.kt:51` — still 30 seconds at session end. The session ran out of context mid-task. Completed in the June 21 session as the first action.

### Post-shift town accuracy analysis
The June 20-21 overnight shift happened after this session ended. Town accuracy corrections were collected and applied in the June 21 session:
- Burger King: estimated Pemberton Township → actual Eastampton NJ
- Applebee's: estimated Medford Township → actual Burlington NJ
- Wendy's: estimated Delran Township → actual Burlington NJ
- All GPS-bearing estimates were wrong — expected, as GPS bearing is unreliable when parked

### Town bearing root cause fix
GPS-bearing path in `DeliveryTownEstimator` still active as fallback. The fundamental problem — customer is NOT necessarily along the driver→restaurant direction — was identified and fixed in the June 21 session. The pin-based bearing (pickup_pin→dropoff_pin pixel direction) is the correct approach.

### Stale PinDetector.latestResult clear
`PinDetector.latestResult = null` was only executed inside the `success == true` branch, meaning a failed detection result would persist and could bleed into the next offer. Fixed June 21 by moving the clear to before any branch check.

### Pill heading town display
Delivery town in the collapsed PILL state was not implemented this session. Done in June 21 as a new feature (Pro-tier marker).

### 0 town confirmation notifications tapped
`confirmedTown` and `townAccurate` are null on all 15 DB rows. Driver has not tapped Yes/No on any town accuracy notification. Data needed to measure accuracy improvement from pin-detection.

---

## Known Broken (do not ignore)

### GPS-bearing town estimator still dominant in DB
IDs 1–9 and 15 in `offer_captures` were estimated via `gps_bearing` — all likely wrong. The new pin_detection path (v0.1.194+) will produce better estimates for future offers but old rows cannot be retroactively fixed. DB shows `estimatedTownMethod = 'gps_bearing'` for these.

### PinDetector.latestResult stale-result risk (fixed June 21)
In this session's code, if `PinDetector.detect()` returns `success=false`, `latestResult` remains set with the failed result. The next offer's `DeliveryTownEstimator` call checks `pinResult?.success == true` and skips it, but the failed result persists for the offer after that. Fixed June 21 by always nulling `latestResult` at entry to `estimateTown()`.

### Missing restaurant on IDs 12, 13, 14 (Jun 21 shift, 12:28–12:43 AM)
Three consecutive offers have null restaurant — OCR/accessibility service didn't parse them. Likely a DoorDash UI variant (stacked offers? different popup layout?). Check `debug/screen_texts.log` and `debug/map_debug.log` from those timestamps.

### Missing order #7 from Jun 20–21 shift
7-Eleven Burlington → Burlington NJ delivery never captured at all. GigLens was running but never fired. Possible causes: batched/stacked order in different UI layout, offer came as notification-only, accessibility service missed trigger keywords. Check Crashlytics `looksLikeOfferScreen` logs from ~12:43–1:07 AM window.

### Pre-existing compiler warnings (unchanged)
- `OfferDetectorService.kt`: `mode` variable unused; multiple `recycle()` deprecated calls
- `SettingsActivity.kt`: delicate API warnings on several lines
- `AccessibilityOfferReceiver.kt`: Elvis operator always returns left operand
- None block the build

### AUTO_CAPTURE_MODE default still "accessibility"
Set to `"accessibility"` for dev convenience (prior session). Must decide explicitly before Play Store public release whether this is the right default for real drivers or should revert to `"off"`.

---

## Next Session — Start Here

**Note: The June 21 session already handled steps 1–4 and part of 5. These are preserved for reference.**

1. **First: verify the dedup fix is complete.** Check `OfferDetectorService.kt:51` — `OFFER_DEDUP_WINDOW_MS` should be `120_000L`. If still `30_000L`, apply the same fix as `AccessibilityOfferReceiver`. *(Completed June 21)*

2. **Pull DB after next shift and check town accuracy.** Run with `PIN_DETECTION_ENABLED=true`. Look for `estimatedTownMethod = 'pin_detection'` rows — these should be more accurate than the `gps_bearing` rows:
   ```sql
   SELECT restaurant, estimatedTown, estimatedTownMethod, confirmedTown, townAccurate
   FROM offer_captures ORDER BY id DESC LIMIT 10;
   ```

3. **Tap Yes/No on town confirmation notifications during shifts.** Zero confirmations logged as of June 21. `confirmedTown` and `townAccurate` will remain null until the driver actively responds. Required to measure pin-detection improvement vs GPS-bearing.

4. **Investigate missing restaurant on IDs 12–14.** Pull `debug/screen_texts.log` and `debug/map_debug.log` from device:
   ```bash
   adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) shell \
     "run-as com.augusteenterprise.giglens cat \
     /data/data/com.augusteenterprise.giglens/files/debug/screen_texts.log" \
     | grep "12:2[0-9]\|12:4[0-9]"
   ```

5. **Investigate missing order #7 (7-Eleven Burlington).** Check Firebase Crashlytics for `looksLikeOfferScreen` logs from 12:43–1:07 AM on Jun 21. If `isOffer=false` during that window, the accessibility service saw DoorDash foregrounded but didn't recognize the screen as an offer.

6. **Decide AUTO_CAPTURE_MODE default** before any non-internal Play Store release. Current: `"accessibility"` (auto-capture, set for dev convenience). For real drivers: consider keeping `"accessibility"` (seamless) or reverting to `"off"` (explicit opt-in). This interacts with the still-unbuilt Accessibility Disclosure Screen.

7. **Build Accessibility Disclosure Screen** (ViewBinding, not Compose). Shown once before first Accessibility Service permission request. Content already drafted in FEATURE_BACKLOG.md.

8. **Phase 1 scoring redesign** — GREEN/YELLOW/RED with configurable thresholds in Settings.

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated across multiple shifts |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | pay/distance/restaurant/deliverBy all correct |
| Offer dedup guard — AccessibilityOfferReceiver | ✅ Fixed | 2min window (was 30s) — this session |
| Offer dedup guard — OfferDetectorService | ⚠️ Partial | Still 30s at session end — fixed June 21 |
| GPS-bearing town estimation | ⚠️ Active but inaccurate | Wrong on all June 20-21 shift orders — bearing is stale when parked. Replacement in production. |
| PinDetector.kt | ✅ Built | Pure Kotlin HSV blob detection — this session |
| PinDetectionTownEstimator.kt | ✅ Built | Pixel calibration → bearing → Nominatim — this session |
| PIN_DETECTION_ENABLED config key | ✅ Built | Seeded via seedIfAbsent() on service connect — this session |
| Pin detection wired into screenshot flow | ✅ Built | detect() called in onSuccess, latestResult available to estimateTown() — this session |
| DeliveryTownEstimator dispatch (pin → GPS fallback) | ✅ Built | Stale-result clear bug remains (fixed June 21) |
| Pill revert timer bug | ✅ Fixed | coerceAtMost(30) → coerceIn(5, 300) — this session |
| Pin Detection toggle in Settings | ✅ Built | switchPinDetectionEnabled, loads/saves from DB — this session |
| Settings page design | ✅ Rebuilt | Matches main page; 3 contrast passes required — this session |
| Dark mode | ✅ Built | Persistent via SharedPreferences, full color token system — this session |
| testTakeScreenshot() | ✅ Working | Saves PNG + runs PinDetector in onSuccess |
| Screenshot delay (SCREENSHOT_DELAY_MS) | ✅ Working | DB-driven, default 1500ms, configurable in Settings |
| Debug email pipeline (DebugOfferEmailer) | 🔲 Unvalidated | No logcat captured during June 20-21 shift to confirm |
| Overlay pill — town in heading | 🔲 Not started | Done June 21 as Pro feature |
| Accessibility disclosure screen | 🔲 Not started | ViewBinding rebuild pending |
| AUTO_CAPTURE_MODE default | ⚠️ Dev value | Currently "accessibility" — must decide before public launch |
| Phase 1 scoring redesign (GREEN/YELLOW/RED) | 🔲 Scoped | Not started |
| Registration + Stripe billing | 🔲 Scoped | Not started |
| Sentiment Agent repo | 🔲 Scoped | Not started |
| ScreenCaptureService removal | 🔲 Pending | TODO markers in place, 5 files + manifest |
| Order archive view | 🔲 Not started | Day/week/month grouping |
| Play Store public release | 🔲 Blocked | Requires disclosure screen + privacy policy + default review |

---

## Decisions Made This Session

**1. PinDetector implementation unblocked — screenshots confirmed.**
The June 19 session explicitly blocked PinDetector.kt implementation until real-shift screenshots confirmed full pin rendering at 1500ms delay. Screenshots from the June 19 shift (South Star Diner, 10:56 PM) confirmed all three pins visible. Implementation proceeded.

**2. `seedIfAbsent()` is the correct pattern for new config keys.**
New keys must NOT use `INSERT OR REPLACE` (would overwrite user-changed values on upgrade) and must NOT rely solely on `defaultAppConfig()` (only runs on fresh DB creation). `INSERT OR IGNORE` via `seedIfAbsent()` is safe to call on every `onServiceConnected()`.

**3. Pill timer coerceAtMost(30) was a latent bug, not intentional.**
The cap was silently preventing drivers from using the full 5–300s range exposed in Settings. Changed to `coerceIn(5, 300)` — now matches the UI.

**4. Dark mode persists via SharedPreferences, not Room.**
`AppCompatDelegate.setDefaultNightMode()` must be called in `Application.onCreate()` before any Activity. Room reads are async and require a coroutine scope — can't be used synchronously in `onCreate()`. SharedPreferences reads are synchronous and available immediately. SharedPreferences is the right tool for this specific need.

**5. Dark mode toggle must set `isChecked` before attaching the listener.**
Setting `binding.switchDarkMode.isChecked = savedPref` after the listener is attached would fire `AppCompatDelegate.setDefaultNightMode()` immediately, which triggers `Activity.recreate()` in an infinite loop. Always set state first, then wire the listener.

**6. Earnings tile glass effect must NOT be tokenized.**
The four earnings tiles use `android:background="#FFFFFF" android:alpha="0.05"` — a semi-transparent white overlay on `#1A1A2E`. If this were changed to `@color/gl_bg_card`, it would become `#1E1E2E` in dark mode (opaque dark on dark = invisible tiles). Hardcoded hex preserved intentionally here.

**7. TextInputLayout OutlinedBox default stroke is nearly invisible on white.**
`OutlinedBox` stroke defaults to `?attr/colorOnSurface` at reduced opacity — barely visible on white card backgrounds. Must explicitly set `app:boxStrokeColor`, `app:boxStrokeWidth`, and `app:boxStrokeWidthFocused` on every TextInputLayout. This is not documented prominently in Material docs and was discovered empirically during this session.

---

## Files Changed This Session

| File | Change |
|------|--------|
| `town/PinDetector.kt` | **NEW** — pure Kotlin HSV blob detection, bulk pixel read, flood fill |
| `town/PinDetectionTownEstimator.kt` | **NEW** — pixel calibration → bearing → Nominatim reverse geocode |
| `data/AppConfig.kt` | Added `PIN_DETECTION_ENABLED` key + default; `defaultAppConfig()` updated |
| `data/AppConfigDao.kt` | Added `seedIfAbsent()` method |
| `geocoding/DeliveryTownEstimator.kt` | Added pin-detection dispatch path before GPS-bearing fallback |
| `service/OfferDetectorService.kt` | `onServiceConnected()` seeds PIN_DETECTION_ENABLED; `testTakeScreenshot()` onSuccess calls `PinDetector.detect()` |
| `service/OfferOverlayService.kt` | Pill timer `coerceAtMost(30)` → `coerceIn(5, 300)` |
| `service/AccessibilityOfferReceiver.kt` | `DEDUP_WINDOW_MS` 30s → 120s (2 minutes) |
| `ui/SettingsActivity.kt` | `switchPinDetectionEnabled` load/save; `switchDarkMode` load/save |
| `res/layout/activity_settings.xml` | Full redesign (3 passes); added APPEARANCE section with `switchDarkMode`; added CAPTURE SETTINGS section with `switchPinDetectionEnabled` |
| `res/layout/activity_main.xml` | All hardcoded hex → `@color/gl_*` tokens |
| `res/values/colors.xml` | **NEW** — 10 light-mode color tokens |
| `res/values-night/colors.xml` | **NEW** — 10 dark-mode color token overrides |
| `GigLensApp.kt` | `PREFS_NAME`/`PREF_DARK_MODE` constants; `AppCompatDelegate.setDefaultNightMode()` in `onCreate()` |
| `docs/WORKING_AGREEMENTS.md` | ADB session-start rule added (always `adb connect` before any adb command) |

---

## Features Left to Implement (Priority Ranked)

1. **Verify dedup fix is complete** — confirm `OfferDetectorService.OFFER_DEDUP_WINDOW_MS = 120_000L`. *(Done June 21)*
2. **Town bearing fix** — GPS fallback in `DeliveryTownEstimator` projects using driver→restaurant bearing, which is also wrong (customer is not necessarily along that direction). Must use pin-to-pin bearing from pin positions, fall through to `unavailable` when no pins. *(Done June 21)*
3. **Tap Yes/No town confirmation notifications** — `townAccurate` column empty on all rows; can't measure accuracy without data
4. **Investigate missing restaurant (IDs 12–14) and missing order #7** — check debug logs from Jun 21 shift 12:28–1:07 AM window
5. **Accessibility Disclosure Screen** — ViewBinding rebuild, shown before first accessibility permission request. Content already drafted.
6. **Phase 1 scoring redesign** — GREEN/YELLOW/RED with configurable thresholds in Settings
7. **Decide AUTO_CAPTURE_MODE default** before public launch (currently "accessibility" for dev convenience)
8. **Pro feature gate implementation** — delivery town in pill heading marked as Pro; gate logic not yet wired
9. **Debug email validation** — no logcat was running during shifts to confirm DebugOfferEmailer fires
10. **ScreenCaptureService removal** — 5 files + manifest, TODO markers in place
11. **Order archive view** — day/week/month grouping, fix chart scope
12. **Registration + Stripe billing** — SQLCipher migration + FastAPI backend (see FEATURE_BACKLOG.md for full spec)
13. **Play Store public release** — store listing, screenshots, content rating, privacy policy, Fastlane Ruby fix
14. **Sentiment Agent repo** — separate project (see FEATURE_BACKLOG.md)

---

*Next developer: read WORKING_AGREEMENTS.md first — the ADB connect-first rule and patch script hygiene rules apply to every session. KEY CONTEXT: pin-detection is now wired and running live. The GPS-bearing fallback in DeliveryTownEstimator is still active but known-wrong when driver is stationary (which is always, for offers). The June 21 session replaced it with a 3-tier system: full pin detection → partial pin detection → unavailable (no projection). Town estimates for all rows before June 21 (IDs 1–15) used gps_bearing and are likely wrong. The correct baseline for accuracy measurement starts with the first offer captured after June 21 v0.1.208 deploys.*
