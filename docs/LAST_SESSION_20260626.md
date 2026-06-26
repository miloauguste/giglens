# GigLens — Session Handover (2026-06-26)

**Version at session start:** 0.1.257 (Pixel sideload)
**Version at session end:** 0.1.259 (committed; Pixel running 0.1.259 debug)
**Branch:** `feat/driver-anchored-ab` (NOT merged to main, NOT pushed)
**Conducted by:** Claude (Opus 4.8) — last-shift investigation → pin-detection crop fix + town-projection A/B

---

## What this session did

Investigated the last shift, root-caused why town estimation kept returning
`unavailable`, fixed it, and added a shadow A/B to compare two town-projection
methods. All image analysis was validated offline (Python) against the 8 saved
screenshots before any build.

### 1. Last-shift investigation (DB pulled from Pixel, WAL-checkpointed)
DB had been reset — only 8 offers total. Last real shift = **2026-06-24** (ids 4–7,
4 offers); plus stray id 8 (Wawa, today) and 06-23 ids 1–3.

- Shift economics (06-24): $28.74 pay / 19.1 mi / $24.22 net / $1.50 true $/mi; 3 TAKE, 1 SKIP. Driver is DoorDash **Platinum**.
- **`townAccurate` is null on all 8 rows** — driver has STILL never tapped a Yes/No
  confirmation. This blocks all town-accuracy scoring (every session since 06-20).
- **`accepted` is null on all rows** — app records offers shown, not which were taken.
- **`timeCost` hardcoded 0.0** in `OfferScorer.kt:71` — net value = pay − vehicle cost only.
- **`scorer_config` table is empty** — verdict thresholds are hardcoded (relevant to the
  still-pending Phase-1 scoring redesign). GREEN/YELLOW/RED structure already exists in
  `OfferScorer.kt:84` (TAKE/BORDERLINE/SKIP on profitPct); `score` column = profit %, not 0–100.

### 2. Root cause of `unavailable` towns — FIXED
The "Spain" (id3) bug was **already fixed by v254** (restaurant geocode with null GPS;
id3 is pre-fix stale data). The live failure (id8 Wawa) was different:

- **The fixed 8–32% screenshot crop cut the driver dot out of frame.** Driver dot sat at
  y=49%; crop bottom was 32%. PinDetector literally never received those pixels
  (`blue=0 driverDot=null` in the shift log was a framing error, NOT a color/threshold issue —
  the dot's color was sat 203/val 255, passes the gate trivially).
- The DoorDash map height varies per offer (offer-card height changes), so a fixed crop
  fraction clips the map on tall-map offers. Map truly occupies ~18–55% on id8.

### 3. Fixes shipped (commit `fc7416c`, v0.1.259)
- **Dynamic map-band crop** (`OfferDetectorService.detectMapBand()`): finds the longest
  contiguous run of navy-map rows (Mapbox dark theme has blue cast B>R) between the banner
  above and the dark offer card below. Fixed-window fallback if detection fails. Kotlin port
  verified to reproduce the validated Python bands exactly.
- **Mapbox attribution filter** (`PinDetector` MAPBOX_ZONE): bottom-left exclusion kills the
  ~51px "mapbox" logo blob the wider crop exposed (was mis-counted as a delivery pin).
- **Driver-anchored shadow A/B** (`PinDetectionTownEstimator`): alongside the primary
  restaurant-anchored projection, also projects the dropoff anchored on the KNOWN driver GPS
  at the driver-dot pixel (no restaurant geocode → structurally Spain-proof). Records
  COORDS ONLY in `rawOcrText` (`altLat=.. altLon=.. altMethod=driver_anchored`) — no DB
  migration, no 2nd Nominatim call, no pill latency. Primary behavior UNCHANGED.

### Offline validation result (id1–id8)
- Driver dot recovered: **2/8 → 8/8** with dynamic crop.
- Fully solvable (dot + 2 real pins): **2/8 → 6/8**. Remaining 2: id4 (pickup pin rendered
  off the top of the map — unrecoverable by crop), id8 (pickup pin clipped at top / under banner).
- Driver-anchored vs restaurant-anchored disagreed on id5 (Moorestown vs Pennsauken) and id6
  (Moorestown vs Mt Laurel); agreed on id7 (Hainesport). **Cannot adjudicate without
  confirmation data.** Caveats: road-vs-crow-flies scale overcalibration; id5 had a false 3rd pin.

---

## Files changed (commit fc7416c)
| File | Change |
|------|--------|
| `service/OfferDetectorService.kt` | `detectMapBand()` dynamic crop + fixed fallback; ShiftLogger import |
| `town/PinDetector.kt` | MAPBOX_ZONE bottom-left white-blob exclusion |
| `town/PinDetectionTownEstimator.kt` | driver GPS params; driver-anchored shadow projection; alt coords in TownEstimate |
| `geocoding/DeliveryTownEstimator.kt` | TownEstimate altLat/altLon/altMethod fields; pass driver GPS to estimator |
| `service/AccessibilityOfferReceiver.kt` | append alt coords to rawOcrText |

Analysis artifacts saved in `docs/security-reports/`: `id8_wawa_AS_CROPPED_8-32pct.png`,
`id8_wawa_PROPOSED_16-56pct.png`, `id7_tacobell_DYN_annotated.png`, `id8_wawa_DYN_annotated.png`.
Offline scripts in `/tmp`: `validate_crop.py`, `driver_anchored.py` (not committed — regenerate if needed).

---

## ⚠️ TESTING NEEDED / CALLS TO ACTION (do this next shift)

This build is on the Pixel (v0.1.259 debug) but is **NOT validated in the wild yet.** The
branch stays on `feat/driver-anchored-ab` until it is. Concrete checks:

1. **TAP YES/NO ON EVERY TOWN-CONFIRMATION NOTIFICATION.** This is the #1 blocker — without
   `confirmedTown`, none of the crop fix or the A/B can be scored. Target 5–10 confirmations.

2. **Confirm the dynamic crop works in the wild.** After the shift:
   ```
   adb connect 10.0.0.110:$(cat ~/giglens/.pixel_port)
   adb -s <dev> shell run-as com.augusteenterprise.giglens cat files/shift_2026-06-2X.log
   ```
   - Look for `crop y=.. (dynamic)` lines — confirm it says `dynamic`, not `fixed` (fallback).
   - Look for `success=true driverDot=(..)` — pin detection should now succeed on MORE offers
     than before (was ~2/8). If still mostly `success=false driverDot=false`, the band detector
     may be mis-firing on a layout we haven't seen — pull that screenshot and re-validate offline.

3. **Confirm the A/B is logging.** In the shift log, look for:
   `AB driver-anchored=LAT,LON restaurant-anchored=LAT,LON`
   And in the DB, `rawOcrText` should contain `altLat=.. altLon=.. altMethod=driver_anchored`
   on full-pin offers. If `altLat` is missing, driver GPS was null (check GPS warning strip).

4. **Post-shift A/B scoring (Claude does this).** Pull DB, reverse-geocode each `altLat/altLon`
   offline, build the head-to-head table: `confirmedTown` vs restaurant-anchored `estimatedTown`
   vs driver-anchored. Decide which projection wins; if driver-anchored ≥ restaurant-anchored,
   promote it to primary (it's also Spain-proof). Re-check the road-vs-crow-flies scale factor
   against confirmed towns.

5. **Crash watch.** `adb -s <dev> logcat -d | grep "FATAL EXCEPTION"` after the shift — the
   dynamic crop adds a per-screenshot `getPixels` row scan; confirm no OOM/ANR on real captures.

6. **Negative check (id4/id8 class).** If an offer's pickup pin is off the top of the map or under
   the banner, town should still be `unavailable` (not a wrong town). Verify those degrade gracefully.

---

## Branch / merge state
- `feat/driver-anchored-ab` @ `fc7416c` — committed, NOT pushed, NOT merged to main.
- Merge to main only after the next shift validates the crop + collects A/B/confirmation data.
- Pixel running v0.1.259 debug (matches commit).

## Still pending (unchanged priority order)
1. **Phase 1 scoring redesign** (GREEN/YELLOW/RED configurable thresholds, seed `scorer_config`) — still not started.
2. Town accuracy validation — gated on confirmation taps (see CTA #1).
3. Decide driver-anchored vs restaurant-anchored primary (gated on CTA #4).
4. AUTO_CAPTURE_MODE default decision (pre-launch), accessibility disclosure screen, Pro gate for pill town.
