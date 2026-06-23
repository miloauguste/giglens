# GigLens — Session Handover (2026-06-23)

---

## Session Summary — Town detection timing fix + race condition audit (2026-06-23)
**Version at session start:** 0.1.233  
**Version at session end:** 0.1.234  
**Conducted by:** Claude (Anthropic)

---

## What Was Found

### Post-shift investigation: all towns still `unavailable`
Driver returned from shift reporting no towns came through on any offer. DB showed IDs 22–23 both `estimatedTownMethod=unavailable`. Logcat buffer had rolled over; no debug screenshots were present in the external files dir. The PinDetector fixes from the previous session (BLOB_MAX_PX 5000→30000, compactness filter) were confirmed committed in v0.1.233.

### Root cause: screenshot fired AFTER the offer was already saved
The timing sequence was wrong from the beginning — this was never a blob detection problem:

**Old (broken) order:**
1. `signalCapture()` → broadcasts `ACTION_OFFER_EXTRACTED` immediately
2. `AccessibilityOfferReceiver` coroutine runs → calls `estimateTown()` → reads `PinDetector.latestResult` (still null)
3. 1500ms later: `testTakeScreenshot()` fires → `PinDetector.detect()` runs → sets `latestResult`

By step 3, the offer had already been processed and saved with `unavailable`. PinDetector's result was always orphaned. This was true for every single offer since pin detection was introduced.

---

## What Was Fixed

### `OfferDetectorService.kt` — screenshot before broadcast (v0.1.234)

**New order:**
1. Extract offer data from accessibility tree immediately (while valid)
2. Delay 1500ms (Mapbox render time)
3. Take screenshot → `PinDetector.detect()` → `PinDetector.latestResult` set
4. Broadcast `ACTION_OFFER_EXTRACTED` with the extracted data
5. `AccessibilityOfferReceiver` reads a populated `latestResult` → town works

**Key changes:**
- `testTakeScreenshot()` replaced with `takeScreenshotThenBroadcast(extracted: ParsedOffer)` — same screenshot/PinDetector logic, but broadcasts the offer at the end instead of silently storing the result
- New `broadcastExtractedOffer(extracted: ParsedOffer)` helper pulls the broadcast code out of `signalCapture()`
- `onAccessibilityEvent` now calls `extractOfferFromNodes(rootNode)` before the coroutine delay, stores the result, and passes it into `takeScreenshotThenBroadcast()`
- Fallback: if API < 30 or accessibility extraction fails, old `signalCapture()` path is used (no pin detection)

**Side effect:** The offer pill appears ~1500ms later than before (screenshot must complete before broadcast). This is acceptable — DoorDash gives 30–60 seconds to decide.

---

## Race Conditions Identified (not yet fixed)

Brainstormed post-fix. Ordered by priority:

### 🔴 1. `lastOfferFingerprint` / `lastOfferBroadcastMs` — cross-thread write (introduced by this fix)
`broadcastExtractedOffer()` now runs on the executor thread (screenshot callback) and writes `lastOfferFingerprint` and `lastOfferBroadcastMs`. `onAccessibilityEvent()` reads and writes the same fields on the accessibility thread. Both are plain `var`, not `@Volatile`. Worst case: stale read → dedup fails → duplicate DB row.

**Fix:** Add `@Volatile` to both fields.

### 🔴 2. `lastOfferFingerprint` namespace collision — pre-existing, now exposed
The same field stores two incompatible formats:
- `onAccessibilityEvent()` writes a screen-hash (e.g. `-1234567890`)
- `broadcastExtractedOffer()` writes `"9.5:3.2"`

The dedup check in `broadcastExtractedOffer()` compares `"9.5:3.2"` against whatever the accessibility thread last wrote — they never match, so the receiver-level dedup never fires. The `AccessibilityOfferReceiver` companion-object dedup is the only thing preventing duplicates.

**Fix:** Split into two separate fields — `lastScreenFingerprint` (screen-level, accessibility thread) and `lastBroadcastFingerprint` / `lastBroadcastMs` (broadcast-level, executor thread).

### 🟡 3. `PinDetector.latestResult` read-clear is non-atomic
In `estimateTown()`:
```kotlin
val pinResult = PinDetector.latestResult  // read
PinDetector.latestResult = null           // clear
```
`@Volatile` ensures visibility but not atomicity. Two concurrent `estimateTown()` calls could both read the same result before either clears it. Extremely unlikely (DoorDash shows one offer at a time), but not thread-safe by construction.

**Fix:** `AtomicReference<PinDetectionResult?>` with `.getAndSet(null)`.

### 🟡 4. `OfferDetectorService.isRunning` / `ScreenCaptureService.isRunning` — missing `@Volatile`
Both flags are written on their respective service threads but read on the accessibility thread. A stale `false` read of `ScreenCaptureService.isRunning` would silently suppress the OCR fallback for an entire offer.

**Fix:** Add `@Volatile` to both.

### 🟡 5. `AccessibilityOfferReceiver` companion state + `goAsync()` concurrency
`lastFingerprint` and `lastInsertMs` in the companion object are checked before the coroutine launches but updated inside it on `Dispatchers.IO`. If two `ACTION_OFFER_EXTRACTED` broadcasts arrive within milliseconds (before either coroutine runs), both could pass the fingerprint check simultaneously → two DB inserts for the same offer.

**Fix:** Move the fingerprint check inside the coroutine, or use a `Mutex` around the check-and-update.

### 🟢 6. Nominatim — no request serialization
`DeliveryTownEstimator` is a singleton but makes no attempt to serialize HTTP requests. Back-to-back offers (decline → immediate new offer) fire two concurrent Nominatim searches. Nominatim's rate limit is 1 req/sec — the second request gets 429, returns null, and the town is `unavailable`.

**Fix:** A `Mutex` around the HTTP calls in `DeliveryTownEstimator`.

### 🟢 7. GPS location fetched with no timestamp validation
`LocationHelper.getCurrentLocation()` may return a stale fix (cached from minutes ago). The location timestamp is never checked — if the driver has moved significantly since the last GPS poll, town estimation projects from the wrong origin.

**Fix:** Validate `location.time` — reject fixes older than ~30 seconds.

---

## Files Changed This Session

| File | Change |
|------|--------|
| `service/OfferDetectorService.kt` | `testTakeScreenshot()` → `takeScreenshotThenBroadcast()`; new `broadcastExtractedOffer()`; `earlyExtracted` before delay in `onAccessibilityEvent` |

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated multiple shifts |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | Retail pickup + digit-start fixed v215 |
| Restaurant extraction (OfferParser) | ✅ Working | Retail pickup + 7-Eleven fixed v215 |
| Town abbreviation in pill | ✅ Fixed | Township/Borough/County stripped, 14-char cap — v215 |
| BLOB_MIN_PX tuned | ✅ Fixed | 200→50 — handles zoomed-out maps — v215 |
| BLOB_MAX_PX + compactness filter | ✅ Fixed | 5000→30000, compactness ≥0.15 — v233 |
| Bitmap crop before PinDetector | ✅ Fixed | 8–46% of screen height — v233 |
| Pill hidden during screenshot | ✅ Built | alpha=0 before shot, restored in all exit paths — v216 |
| zoom=12 in both estimators | ✅ Fixed | Borough/village level — v216 |
| Short-trip confidence guard | ✅ Built | <80px pin sep → confidence="medium" — v216 |
| Offer dedup (both services) | ✅ Fixed | 2-minute window in both services — v211 |
| Town in pill heading | ✅ Built | teal 80% size, abbreviated — v211 |
| **Screenshot → PinDetector → broadcast order** | ✅ Fixed | Was backwards since day 1 — v234 |
| townAccurate confirmations | ⚠️ Not collecting | Driver has not tapped Yes/No — null on all rows |
| Race conditions (#1–#2 cross-thread fingerprint) | 🔴 Not fixed | Highest priority — can cause duplicate DB rows |
| Race conditions (#3–#5 @Volatile / Mutex) | 🟡 Not fixed | Lower risk, worth fixing before scale |
| Scoring redesign (GREEN/YELLOW/RED) | 🔲 Not started | Phase 1 priority |
| Voice readout | 🔲 Not started | Phase 1 |
| Accessibility disclosure screen | 🔲 Not started | Required before Play Store review |
| Registration + Stripe billing | 🔲 Scoped | Phase 2+ |
| ScreenCaptureService removal | 🔲 Not started | Tech debt, TODO markers in place |

---

## Next Session — Start Here

1. **Run a shift and check logcat** — confirm town detection is now working:
   ```bash
   adb logcat -s PinDetector:* DeliveryTownEstimator:* AccessibilityReceiver:*
   ```
   You should see `PinDetector: success=true` followed by `broadcastExtractedOffer` and `estimatedTownMethod=pin_detection` in the DB.

2. **Fix race conditions #1 and #2** — `lastOfferFingerprint` split + `@Volatile`:
   - Split into `lastScreenFingerprint` (screen dedup, accessibility thread) and `lastBroadcastFingerprint` / `lastBroadcastMs` (broadcast dedup, executor thread)
   - Add `@Volatile` to both broadcast fields

3. **Tap Yes/No on town confirmation notifications during the shift.** Still no `townAccurate` data in the DB. Without it the accuracy SQL is useless.

4. **Phase 1 priority: Scoring redesign** — GREEN/YELLOW/RED configurable thresholds. Untouched across Sessions 3–6.
