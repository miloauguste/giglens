# GigLens — Session Handover (2026-06-22)

---

## Session 3 — Dark mode UI fix (2026-06-22)
**Version at session start:** 0.1.222
**Version at session end:** 0.1.226 (deployed to Play Store internal track — confirmed via git log)
**Conducted by:** Claude (Anthropic)

### What was fixed
**Root cause:** `Theme.GigLens` used `DayNight.DarkActionBar` — in dark mode the system action bar rendered white, sitting above the custom dark header whose text is `#FFFFFF`. White text on white bar = invisible.

**Fix 1 — `values/themes.xml`:** Parent changed to `DayNight.NoActionBar`. Eliminates the system action bar entirely. The layout has its own custom dark header (`gl_bg_header`) so the action bar was always redundant.

**Fix 2 — `layout/activity_main.xml`:** Analytics card (NET VALUE TREND) and Verdict Breakdown card (TAKE/BORD/SKIP) had `android:backgroundTint="#FFFFFF"` hardcoded — stayed white in dark mode with light-colored text inside. Changed both to `@color/gl_bg_card` → resolves to `#1E1E2E` in dark mode.

### Deploy
✅ Confirmed deployed. Git log shows commit `4b1e392` — `release: Dark mode fix: top bar washout and analytics card contrast` — confirming `deploy.sh` was run. Pre-commit hook bumped version through to 0.1.226 across all session commits.

### Verify next shift
- Dark mode → main screen top header text visible (not washed out)
- Analytics card (NET VALUE TREND) and Verdict Breakdown card have dark backgrounds, not white

---

## Session 2 — Deploy (2026-06-22, brief)
**Version at session start:** 0.1.221
**Version at session end:** 0.1.222 (deployed to Play Store internal track — confirmed via git log)
**Conducted by:** Claude (Anthropic) — deploy coordination only, no code changes

### What happened
- Milo ran `./deploy.sh "Scoring, accuracy, and noise fixes."` manually
- ✅ Confirmed deployed. Git log shows commit `b61b872` — `release: Bug fixes and improvements.` — confirming deploy completed.
- No code was written or changed this session

---

## Session 1 — Bug triage + accuracy fixes (2026-06-22)
**Version at session start:** 0.1.211 (Play Store internal)
**Version at session end:** 0.1.216 (Play Store internal + Pixel sideload)
**Conducted by:** Claude (Anthropic) — post-shift bug triage + screenshot noise/accuracy fixes

---

## What Was Completed This Session

### 1. Post-shift bug triage — 5 orders from 2026-06-21 shift
- Pulled DB from Pixel, ran WAL checkpoint, queried offer_captures
- Reviewed screenshots for IDs 18–22
- Found and fixed three bugs: retail pickup missing restaurant, town truncation in pill, Taco Bell pin detection returning `unavailable`

### 2. Bug fix: Retail pickup restaurants showing null restaurant
**Root cause:** `extractOfferFromNodes()` in OfferDetectorService matched `lower == "pickup"` exactly. DoorDash uses "Retail pickup" for Wawa, 7-Eleven, CVS, etc. — never matched.

Also: `extractRestaurant()` Strategy 1 in OfferParser skipped names starting with a digit (`candidate[0].isUpperCase()` only) — rejected "7-Eleven" (starts with '7').

**Fix in OfferDetectorService.kt:**
```kotlin
// Before:
if (restaurant == null && lower == "pickup" && i + 1 < texts.size) {
// After:
if (restaurant == null && (lower == "pickup" || lower == "retail pickup") && i + 1 < texts.size) {
```

**Fix in OfferParser.kt** — Strategy 1 trigger + digit-start allowed:
```kotlin
if (cleaned == "pickup" || cleaned == "pick up"
    || cleaned == "retail pickup"
    || cleaned.contains("delivery for")
    || cleaned.contains("pick up from")
) {
    ...
    if (candidate.length in 2..50
        && candidate.isNotEmpty()
        && (candidate[0].isUpperCase() || candidate[0].isDigit())  // was isUpperCase() only
```
Same digit-start relaxation applied to Strategy 2.

### 3. Bug fix: Town text truncating in pill heading
**Symptom:** "Lumberton Township" displayed as "Lumbertor T", "Southampton Township" displayed as "Southampt..."

**Root cause:** No pre-truncation cleanup — Nominatim returns "Southampton Township" as the raw name, then it gets hard-truncated mid-word.

**Fix in OfferOverlayService.kt** — added `abbreviateTown()`:
```kotlin
private fun abbreviateTown(raw: String): String {
    val stripped = raw
        .replace(Regex(" Township$", RegexOption.IGNORE_CASE), "")
        .replace(Regex(" Borough$", RegexOption.IGNORE_CASE), "")
        .replace(Regex(" County$", RegexOption.IGNORE_CASE), "")
        .trim()
    return if (stripped.length > 14) stripped.take(12) + "…" else stripped
}
```
Called from `pillTextWithTown()` after stripping the "📍 ~" prefix.

### 4. Bug fix: Taco Bell pin detection returning `unavailable`
**Root cause:** The Taco Bell offer (Hainesport → Eastampton, ~10mi span) caused a zoomed-out map. Driver dot was ~12–15px diameter (~113–177px area at STEP=3). Old `BLOB_MIN_PX = 200` filtered it out.

**Fix in PinDetector.kt:**
```kotlin
// Before:
private const val BLOB_MIN_PX = 200
// After:
private const val BLOB_MIN_PX = 50
```
Minimum blob area now corresponds to ~8px diameter — handles driver dots in wide-area maps.

**Deployed v0.1.215** after these three fixes.

---

### 5. Town accuracy root-cause analysis — IDs 19 and 20
User provided ground-truth corrections:
- **ID 19:** estimated Lumberton Township → actual **Mt. Holly NJ**
- **ID 20:** estimated Southampton Township → actual **Pemberton NJ**

Two distinct root causes identified:

**ID 20 (Southampton → Pemberton):** Nominatim `zoom=10` returns township/municipality level. Pemberton Borough is entirely contained within Southampton Township. `zoom=12` returns borough/village level. Geographic projection was correct — this was a naming precision issue only.

**ID 19 (Lumberton → Mt. Holly):** 2.3mi total trip → pickup→dropoff pins ~40–60px apart. At that scale, a 5px centroid error produces ~5–8° bearing rotation → ~0.18mi displacement at 1.5mi delivery leg → enough to cross an NJ township boundary. Possible pin swap as well (driver equidistant from both pins on short deliveries). Not fixable by adding a distance buffer — the road-vs-crow-flies calibration already overcalibrates, so adding a buffer would worsen all projections.

**User asked about 10% pixel mileage buffer:** Rejected. Road miles (DoorDash total) > crow-flies pixels → calibration already projects too far. Buffer would make Pemberton (zoom issue, not distance issue) and Mt. Holly (bearing issue, not distance issue) both worse.

### 6. Screenshot noise reduction — bitmap crop + pill hide
Two noise sources identified that create false white blobs in PinDetector:
1. DoorDash heads-up notification banner (top ~8–10% of screen) — white DoorDash logo and text triggers V>200, S<30 white blob filter
2. GigLens pill overlay — draggable, can be anywhere on map; white text characters can form blobs above BLOB_MIN_PX=50

**Fix 1 — Bitmap crop in `testTakeScreenshot()` onSuccess** (OfferDetectorService.kt):
```kotlin
val cropTopPx    = (softwareBitmap.height * 0.08).toInt()
val cropHeightPx = (softwareBitmap.height * 0.24).toInt()
    .coerceAtMost(softwareBitmap.height - cropTopPx)
val mapBitmap = android.graphics.Bitmap.createBitmap(
    softwareBitmap, 0, cropTopPx, softwareBitmap.width, cropHeightPx
)
val pinResult = PinDetector.detect(mapBitmap)
mapBitmap.recycle()
```
For a 2404px screen: captures y=192–769 (map is typically y=210–640). Excludes notification banner (top) and offer text/accept button (bottom).

**Fix 2 — Pill hidden during screenshot** (OfferOverlayService.kt + OfferDetectorService.kt):
- `OfferOverlayService` now tracks itself via `@Volatile private var instance` in companion object
- Companion exposes `hideForScreenshot()` and `showAfterScreenshot()` — both post to main looper, set `rootView?.alpha = 0f / 1f`
- `OfferDetectorService` calls `hideForScreenshot()` after the Mapbox delay, waits 50ms for render, then fires screenshot
- Pill is restored in onSuccess, onFailure, and catch block — no case where pill stays invisible

### 7. Fix: zoom=10 → zoom=12 in both town estimators
Changed in both files:
- `app/src/main/java/com/augusteenterprise/giglens/town/PinDetectionTownEstimator.kt:129`
- `app/src/main/java/com/augusteenterprise/giglens/geocoding/DeliveryTownEstimator.kt:238`

`zoom=12` returns borough/village/town level instead of township/municipality. This fixes the Southampton → Pemberton class of error.

### 8. Short-trip confidence guard
Added to `PinDetectionTownEstimator.estimate()` after pixel calibration:
```kotlin
val confidence = if (pickupToDropoffPx < 80f) {
    Log.w(TAG, "estimate: short trip — pickupToDropoffPx=$pickupToDropoffPx px < 80 → confidence=medium")
    "medium"
} else {
    "high"
}
```
Pin separations under 80px indicate short delivery legs where bearing errors are amplified. The result is still returned (not suppressed), but confidence is "medium" so the driver knows the estimate is rough. The 80px threshold corresponds roughly to a 1–2mi delivery leg in a typical zoomed map.

### 9. Deployed v0.1.216
All fixes in this session: bitmap crop, pill hide, zoom=12, short-trip guard. Play Store internal track + Pixel sideload.

---

## Files Changed This Session

| File | Change |
|------|--------|
| `service/OfferDetectorService.kt` | Retail pickup match; pill hide before screenshot; bitmap crop before PinDetector; pill restore in all exit paths |
| `ocr/OfferParser.kt` | Strategy 1 + 2 trigger "retail pickup"; digit-start allowed for "7-Eleven" type names |
| `service/OfferOverlayService.kt` | `abbreviateTown()` to strip suffixes; `instance` companion tracking; `hideForScreenshot()` / `showAfterScreenshot()` |
| `town/PinDetector.kt` | `BLOB_MIN_PX` 200→50 |
| `town/PinDetectionTownEstimator.kt` | `zoom=10`→`zoom=12`; short-trip confidence guard (< 80px → "medium") |
| `geocoding/DeliveryTownEstimator.kt` | `zoom=10`→`zoom=12` |

---

## Known Open Issues

### Town accuracy — still collecting data
`townAccurate` is null on all DB rows — driver has not tapped Yes/No on any town confirmation notification across any shift yet. Accuracy SQL query cannot be run without confirmation data. Tap Yes/No on notifications during the next shift.

### ID 19 (Mt. Holly) root cause not fully solved
`zoom=12` fixes the Pemberton class of error. Short-trip guard flags the Mt. Holly class as "medium" confidence but doesn't improve the estimate itself. True fix would require:
- Better centroid estimation (sub-pixel smoothing), OR
- A minimum-confidence threshold below which we return "unavailable" rather than a wrong town
Decision deferred until accuracy data shows how prevalent short-trip errors are.

### Crop boundaries are approximate
The 8%–32% crop is calibrated to a 2404px Pixel 10 screen. On different screen heights the map region may shift. If accuracy regressions appear on other devices, the crop fractions may need per-device tuning or dynamic detection.

### Pre-existing warnings (not regressions)
- OfferDetectorService.kt: unused `mode` var, 5x `recycle()` deprecated
- OfferOverlayService.kt: `params` name shadow (line 488)
None block the build.

---

## Next Session — Start Here

1. **Run a shift with Yes/No confirmation taps.** The DB has rows but `townAccurate` is null everywhere. Without this data the accuracy SQL query is useless. After 5-10 confirmations:
   ```sql
   SELECT estimatedTownMethod,
     COUNT(*) as total,
     SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) as correct,
     ROUND(100.0 * SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) / COUNT(*), 1) as accuracy_pct
   FROM offer_captures
   WHERE estimatedTown IS NOT NULL AND townAccurate IS NOT NULL
   GROUP BY estimatedTownMethod;
   ```

2. **After shift, pull screenshots and check logcat** for short-trip warnings:
   ```bash
   adb logcat -d | grep "short trip"
   ```
   If short-trip orders are common (> 30% of offers), consider lowering confidence to "unavailable" for those rather than showing a potentially wrong town.

3. **Phase 1 priority: Scoring redesign.** GREEN/YELLOW/RED configurable thresholds in Settings. This is the next dedicated feature work — not a bug fix.

4. **Check crop effectiveness.** In logcat, look for:
   ```
   Map crop: y=192..769 of 2404px (1080x577)
   ```
   If pin detection accuracy improves (fewer `unavailable` results on offers where pins were previously missed), the crop is working.

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated multiple shifts |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | Retail pickup + digit-start fixed v215 |
| Restaurant extraction (OfferParser) | ✅ Working | Retail pickup + 7-Eleven fixed v215 |
| Town abbreviation in pill | ✅ Fixed | Township/Borough/County stripped, 14-char cap — v215 |
| BLOB_MIN_PX tuned | ✅ Fixed | 200→50 — handles zoomed-out maps — v215 |
| Bitmap crop before PinDetector | ✅ Built | 8–32% of screen height captures map only — v216 |
| Pill hidden during screenshot | ✅ Built | alpha=0 before shot, restored in all exit paths — v216 |
| zoom=12 in both estimators | ✅ Fixed | Borough/village level — v216 |
| Short-trip confidence guard | ✅ Built | <80px pin sep → confidence="medium" — v216 |
| Offer dedup (both services) | ✅ Fixed | 2-minute window in both services — v211 |
| Town in pill heading | ✅ Built | teal 80% size, abbreviated — v211 |
| PIN_DETECTION_ENABLED config | ✅ Built | Seeded via seedIfAbsent() |
| Pin detection (3-tier) | ✅ Active | full pin → partial pin → unavailable — v208 |
| Settings + dark mode | ✅ Built | v205 |
| Play Store deploy pipeline | ✅ Working | fastlane supply, auto-version via pre-commit hook |
| townAccurate confirmations | ⚠️ Not collecting | Driver has not tapped Yes/No — null on all rows |
| Scoring redesign (GREEN/YELLOW/RED) | 🔲 Not started | Phase 1 priority |
| Voice readout | 🔲 Not started | Phase 1 |
| Accessibility disclosure screen | 🔲 Not started | Required before Play Store review |
| Registration + Stripe billing | 🔲 Scoped | Phase 2+ |
| Sentiment Agent repo | 🔲 Not started | Separate repo, Phase 2+ |
| Order archive view | 🔲 Not started | Phase 2 |
| ScreenCaptureService removal | 🔲 Not started | Tech debt, TODO markers in place |
