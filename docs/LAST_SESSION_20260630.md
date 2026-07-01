# GigLens — Session Handover (2026-06-30)

**Device at session start:** v0.1.259 (Pixel) · **at session end:** v0.1.261
**Branches touched:** `feat/driver-anchored-ab` (active, v0.1.261) · `feat/platform-registry` (new, parked design note, v0.1.262)
**Conducted by:** Claude (Opus 4.8, 1M context)
**State:** neither branch merged to `main`. Both pushed to origin.

---

## What this session did

Investigated why town-accuracy data has never been collected, root-caused it, built the
fix, analyzed the 06-27 shift's actual deliveries offline against saved screenshots,
built a processing-pill + banner-clear delay to fix the deeper pin-occlusion problem, and
brainstormed (design-note only) a per-platform modular architecture for later.

### 1. Root cause — the town-confirmation Yes/No NEVER appeared (not "driver forgot")
Across ~6 sessions `townAccurate`/`confirmedTown` were null on every row. This was **not**
the driver forgetting to tap — the confirmation notification never surfaced:
- `showTownConfirmationNotification()` posts at **`IMPORTANCE_LOW`** (`OfferOverlayService`/
  `AccessibilityOfferReceiver`) → silent, no heads-up, buried in the shade. Confirmed on
  device: channel `town_accuracy` `mImportance=2`. POST_NOTIFICATIONS **is** granted;
  Android 16. So it posts but is invisible to a driving driver.
- Deeper: it fires at **offer time**, but the driver doesn't know the true destination
  until **after** completing the delivery. Wrong question at the wrong moment.

### 2. Fix built — post-shift town confirmation in Offer History (Milo chose this)
Inline Yes/No per row in `OfferHistoryActivity`, shown only when an offer has an estimated
town. **Yes** → `confirmedTown = estimate, accurate = true`; **No** → dialog for the actual
town, `accurate = false`. Reuses the existing `updateTownAccuracy()` DAO — **no migration**.
The only time the driver actually knows the destination.
- **Functionally tested on device** (all passed): strip shows only on town rows; Yes →
  `townAccurate=1`; No + correction → `townAccurate=0, confirmedTown=<entered>`; persists
  across tab switches; no crash. (Test taps wrote fake ground-truth onto real rows 10/13 —
  **reset to null afterward**; DB restored + integrity-verified.)

### 3. Wild-validation of v0.1.259 (the 06-27 shift, ids 9–13)
Pulled the shift log + DB. The v259 fixes work in the wild:
- **Dynamic crop fired 4/5** (1 fell back to fixed).
- **Pin detection `success=true` 5/5** (was ~2/8 before).
- **A/B logged + persisted** on the 2 full-pin offers (Moorestown, Pemberton).

### 4. Actual delivery towns from the screenshots (offline, `docs/security-reports/shift0627_*.png`)
Read straight off the DoorDash maps:

| id | Restaurant | App said | **Actual** | Verdict |
|----|-----------|----------|-----------|---------|
| 9  | Mama's Pizza (catering) | `unavailable` | **Westampton** | ❌ dropoff pin at top edge under banner |
| 10 | Popeyes (**stacked ×2**) | Moorestown | **Lumberton** (Milo confirmed) | ❌ **wrong** — see below |
| 11 | Papa Johns | `unavailable` | **Burlington** | ❌ dropoff pin top-left under banner |
| 12 | Papa John's 1376 | `unavailable` | **Westampton/Mt Holly** | ❌ pin at top |
| 13 | Rita's Italian Ice | Pemberton | **Pemberton** ✓ | ✅ correct (both A/B methods agreed) |

**A/B result:** driver-anchored and restaurant-anchored resolved to the **same township**
on both solvable offers → a tie on this shift. But id10 shows both were **wrong** (Lumberton).

### 5. The id10 stacked-order failure (root-caused, fix deferred per Milo)
`PinDetector` classifies pickup/dropoff **by distance only** (closest-to-driver = pickup,
farthest = dropoff) — the `briefcase`/`house` names are a misnomer; it never reads icon
shape. On the stacked Popeyes offer (offline pixel analysis, `analyze_stacked.py`):
- The two real homes (large ~130px pins) sat **close together, ~65–123px from the driver**.
- A spurious **mapbox logo blob** (50px, bottom-left, dist 510) escaped `MAPBOX_ZONE` in the
  *fixed*-crop fallback (it was at 65% height, filter needs >85%) and **won the "dropoff"
  slot** by being farthest → projected west → Moorestown. Real cluster was next to the
  driver → **Lumberton**.
- Also: the pickup pin was **off-frame under the DoorDash banner** → scale calibration
  (total-distance ÷ visible-pixel-span) is unreliable.

### 6. Milo's key insight → the actual fix built this session
The DoorDash **"New Delivery" notification banner blocks the top of the map** (pickup pin on
stacked orders; dropoff pins on the top-edge misses id9/11/12). **Waiting for the banner to
clear before the screenshot** likely fixes 4 of 5 misses at the source. Built:
- **Processing pill with live countdown** (`OfferOverlayService` `ACTION_SHOW_PROCESSING` +
  `OfferDetectorService`): the pill appears the instant an offer is detected showing
  `$pay ⏳ Ns`, counts down the delay, then morphs to the result + town. Signals "delay in
  progress," not a hang. Reuses the existing `PROCESSING` state; manual camera-tap path
  unchanged.
- **`SCREENSHOT_DELAY_MS` set to 4000ms** (DB `app_config`, no new setting — the countdown
  reads the existing delay). Long enough for the ~5s heads-up to auto-dismiss.
- Verified: compiles, installs (v0.1.261), launches, no crash; `alpha` reset confirmed so
  the pill isn't left invisible. **Not exercised on a real offer yet** (auto-capture is OFF;
  overlay service is non-exported so it can't be triggered from shell).

### 7. Architecture brainstorm — `feat/platform-registry` (design note ONLY, parked)
Milo's driver: maintainability (each platform differs) + privacy/trust. Agreed direction in
`docs/ARCHITECTURE_PLATFORM_REGISTRY.md` (on the `feat/platform-registry` branch):
explicit **registry of supported platforms** (DoorDash only) as single source of truth →
drives the OS-level accessibility `packageNames` filter (structurally blind to non-gig
apps) **and** a Settings "supported apps" surface with a not-monitoring reassurance;
detect by **package name**, not OCR text; **no future platforms shown**; per-platform
module split **deferred until platform #2**.
**Interim rule:** improve mapping/scoring in the **existing** architecture and validate
first — only relocate proven logic into the module structure later.

---

## Files changed / commits

**`feat/driver-anchored-ab`:**
- `8a04a62` — town confirmation (OfferHistory* + `item_offer_row.xml`) + processing pill
  (`OfferOverlayService`, `OfferDetectorService`). (This handover = a further commit.)

**`feat/platform-registry`** (off driver-anchored-ab):
- `b4d2fd0` — `docs/ARCHITECTURE_PLATFORM_REGISTRY.md` + backlog pointer
- `82d8b91` — interim rule (validate → improve in place → relocate later)

**On-device only (not in repo):** `app_config.screenshot_delay_ms = 4000`.
**Analysis artifacts (gitignored):** `docs/security-reports/shift0627_id{9,10,11,12,13}_*.png`,
`/tmp/giglens_session/analyze_stacked.py` (regenerate if needed).

---

## ⚠️ TESTING NEEDED / CALLS TO ACTION (next shift)

**Setup first:** enable **Auto capture** (Settings — it was OFF). Confirm
`SCREENSHOT_DELAY_MS = 4000` in Settings.

1. **Countdown pill** — on each offer the pill must appear immediately as `$<pay> ⏳ 4s`,
   tick `4→3→2→1`, then flip to `+$<net> · 📍 <town>`. *Fail =* blank/frozen pill, no
   countdown, or it never flips to the result.
2. **THE core hypothesis — does waiting fix the blocked pickup?** After the shift:
   `adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) exec-out run-as com.augusteenterprise.giglens cat files/shift_<date>.log`
   - Pull the new `.../files/debug/offer_screenshot_*.png` and eyeball: **is the banner gone
     from the top of the map?** If yes, the wait worked.
   - Stacked/same-facility offers should now detect the **pickup pin** and land a real town
     (not Moorestown-class errors). Top-edge dropoff offers (id9/11/12 class) should land a
     town instead of `unavailable`.
3. **Capture confirmations** — open Offer History post-shift, tap **Yes/No** on the town
   rows. Target 5–10. First real ground truth → unblocks accuracy + A/B scoring. Verify:
   `sqlite3 <db> "SELECT id,estimatedTown,confirmedTown,townAccurate FROM offer_captures WHERE townAccurate IS NOT NULL;"`
4. **Crash / latency watch** — `adb ... logcat -d | grep "FATAL EXCEPTION"`. The 4s delay
   currently delays the **whole** pill (scoring is still post-screenshot). If that feels too
   slow on real offers, next step is to decouple pay/score (instant) from town (after delay).
5. **Negative check** — if a pin is genuinely off-map (not just under the banner), town must
   still degrade to `unavailable`, not a wrong town.

---

## Branch / merge state
- `feat/driver-anchored-ab` @ `8a04a62`+handover — pushed, NOT merged. Device on v0.1.261.
- `feat/platform-registry` @ `82d8b91` — pushed, parked (design note only). Rebase onto
  `main` once driver-anchored-ab merges.
- Merge driver-anchored-ab to main only after the shift validates the countdown + banner
  clear + first confirmations.

## Still pending (priority order)
1. **Next-shift validation** (see CTAs) — gates the merge and all town-accuracy work.
2. **Stacked-order pin routine** — deferred; the banner-clear fix may resolve it (visible
   pickup → correct scale). Re-decide after the shift. If still broken: reject the mapbox
   blob robustly (bottom-left + small ≪ real pins) + cluster the close homes.
3. **Decouple pay/score (instant) from town (post-delay)** — if the 4s whole-pill delay is
   too slow.
4. **Phase 1 scoring redesign** (GREEN/YELLOW/RED configurable thresholds; `timeCost` still
   hardcoded 0.0; `scorer_config` empty) — still not started.
5. Platform-registry implementation — only after mapping/scoring proven (interim rule).
6. Pre-launch: AUTO_CAPTURE_MODE default, accessibility disclosure screen, Pro gate for pill town.
