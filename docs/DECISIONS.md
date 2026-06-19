# GigLens — Decisions Log
<!-- Author: Claude (Anthropic) | Auguste Enterprise -->
<!-- Every significant UI, architecture, or behavior decision goes here. -->
<!-- Format: date → title → decision → rationale → alternatives rejected -->

---

## 2026-06-03 — Session Protocol Adopted

**Decision:** `SESSION_PROTOCOL.md` is mandatory reading before every session
**Rationale:** AI assistants have no memory between sessions; without a structured
handover system, context is lost and rules are repeatedly re-explained from scratch
**Alternatives rejected:** Relying on AI memory (unreliable), verbal rules only (not persisted)
**Approved by:** Milo

---

## 2026-05 — SKIP Verdict: Red Rounded Pill (Octagon Deferred)

**Decision:** SKIP verdict displays as plain red rounded pill (#E24B4A), not a stop-sign octagon
**Rationale:** Octagon was prototyped and mocked up; Milo chose to defer it for simplicity
at current stage. Red pill is visually clear enough for MVP.
**Alternatives rejected:** Stop-sign octagon shape — deferred, not permanently rejected
**Approved by:** Milo

---

## 2026-05 — Countdown Timer: No Blink Animation

**Decision:** Countdown timer under 10 seconds does NOT blink
**Rationale:** Blink animation was added then explicitly removed by Milo — found distracting
**Alternatives rejected:** Blink on `timer <= 10s` — removed per user request
**Approved by:** Milo

---

## 2026-05 — Three-State Widget Interaction Model

**Decision:** Floating pill cycles: IDLE → PILL → MINI → FULL → PILL (tap to cycle)
**Rationale:** Gives drivers quick glance (pill) → more detail (mini) → full breakdown
without requiring explicit close/open actions
**Alternatives rejected:** Two-state (pill ↔ full), modal overlay
**Approved by:** Milo

---

## 2026-05 — Pill Text Format

**Decision:** Pill shows net amount + countdown: e.g. `+$4.37` or `-$4.63 · 58s`
**Rationale:** Drivers need the verdict and time pressure at a glance
**Approved by:** Milo

---

## 2026-05 — File Edit Method: Python Heredoc Only

**Decision:** All file edits must use `python3 << 'EOF'` heredoc scripts with
anchor-based string replacement. No sed, no nano, no direct file writes.
**Rationale:** Prevents accidental overwrites; provides precise, reviewable diffs;
consistent across all AI assistants working on the project
**Approved by:** Milo

---

## 2026-05 — No Hardcoded Values in Business Logic

**Decision:** All configuration values (rates, weights, thresholds) must live in
the Room database (`scorer_config`, `app_config` tables). No literals in Kotlin code.
**Rationale:** Milo explicitly corrected Claude when a region string was hardcoded —
established as a firm project-wide rule
**Approved by:** Milo

---

## 2026-05 — Delivery Leg Distance Cap: 15 Miles

**Decision:** Nominatim geocoding has a hard 15-mile cap on the delivery leg distance
**Rationale:** Outlier distances from geocoding errors would produce absurd scores;
15 miles is a realistic maximum for urban gig delivery
**Approved by:** Milo

---

## 2026-05 — Vehicle Cost Per Mile: IRS Standard $0.90 Default

**Decision:** Default vehicle cost per mile is $0.90 (IRS standard mileage rate),
stored in `scorer_config`, editable by driver
**Rationale:** IRS rate is the defensible industry standard and familiar to drivers
who track mileage for taxes
**Approved by:** Milo

---

## 2026-05 — OCR Dollar/S Misread: Dual Regex

**Decision:** OCR parsing uses dual regex to handle ML Kit misreading `$` as `S`
(e.g. `S4.70` instead of `$4.70`). Both patterns required a decimal point to fire.
**Rationale:** Discovered during real DoorDash offer testing — consistent ML Kit OCR error
**Approved by:** Milo (confirmed by real test: $4.70 / 2.7 mi / Panera Bread)

---

## 2026-05 — Mockup-First UX Policy

**Decision:** No UI implementation proceeds without Milo reviewing a visual mockup first
**Rationale:** Stop-sign octagon session established this — Milo prefers to see it
before it gets built to avoid wasted implementation cycles
**Approved by:** Milo

---

*Add new decisions at the TOP of this file, not the bottom.*
*Template:*
```
## [date] — [title]
**Decision:** [what]
**Rationale:** [why]
**Alternatives rejected:** [what else and why not]
**Approved by:** Milo
```

## 2026-06-04 — Decisions from this session

- **isViewAdded bug resolution:** Fixed and deployed as top priority item from previous session

---

*Next developer: read SESSION_PROTOCOL.md first, then return here.*

## 2026-06-04 — Decisions from this session

- **IPC mechanism:** startService() chosen over BroadcastReceiver for ACTION_SHOW_CAMERA because OfferOverlayService handles signals in onStartCommand(), not via registered receiver
- **Onboarding sequence:** Overlay permission moved to step 0 (before accessibility/capture) to prevent crash on first run
- **Flash timing:** 180ms fade chosen for camera feedback — fast enough to feel instant, slow enough to register visually
- **Grey pill visibility:** Should always be visible when toggle ON — independent of ScreenCaptureService state

---

*Next developer: read SESSION_PROTOCOL.md first, then start with live shift testing to validate OCR accuracy and scoring logic.*

## 2026-06-07 — Decisions from this session

- **Source of truth for widget toggle:** `AppConfigKeys.WIDGET_ENABLED` in DB, not `SharedPreferences` — all code now reads from DB
- **Window recovery approach:** Detect and re-add lost window in `onStartCommand()` instead of service restart — better battery, no flicker
- **Auto-shutdown design:** 60-second countdown when DoorDash closes, auto-disable toggle + stop services — deferred to next session
## 2026-06-19 — Pin-detection town estimator (replaces GPS-bearing as primary)

**Decision:** Replace GPS-bearing-based delivery town estimation with a
computer-vision pin-detection approach as the primary algorithm. GPS-bearing
remains as fallback when CV pin detection fails.

**Algorithm:**
1. Screenshot DoorDash offer screen (existing testTakeScreenshot path)
2. Use OpenCV template matching to locate three pins on the map:
   - Driver location (blue circle, low-saturation)
   - Pickup/restaurant (white circle with black briefcase icon)
   - Dropoff/customer (white circle with black house icon)
3. Compute pixel distances between pins
4. Calibrate miles-per-pixel from offer's stated total distance
   (driver -> pickup -> dropoff) divided by total pixel distance
5. Compute compass bearing from pickup -> dropoff (map is north-up,
   confirmed across multiple screenshots)
6. Project delivery point from restaurant GPS at calibrated distance + bearing
7. Reverse geocode to town name

**Fallbacks:**
- If driver pin not detected: calibrate from real GPS driver->pickup distance
  (computed from driverGPS + geocoded restaurant), confidence=MEDIUM
- If fewer than 2 pins detected: fall back to GPS-bearing estimator
  (existing DeliveryTownEstimator behavior), confidence=LOW
- If GPS-bearing also fails: show "📍 ?" in pill, no wrong answer

**Sanity checks (defense in depth):**
- delivery_leg_miles > 30 -> LOW confidence
- projected point > 50 miles from driver -> LOW confidence
- pixel_total < 50 -> LOW confidence

**Rationale:**
- GPS bearing at low speed is unreliable (confirmed broken across multiple
  shifts; bearing jitter +-45deg at <5mph). This was the core architecture
  limitation flagged in the 2026-06-18 handover.
- Town labels visible on the map (e.g., "Colemantown", "Cox's Corner") are
  REFERENCE geography, not the actual delivery town. OCR on these would
  produce wrong answers (Milo correction during 2026-06-19 design session).
- DoorDash offer distance represents the FULL trip (driver -> pickup -> dropoff),
  not just one leg. This makes it usable as a calibration anchor for
  miles-per-pixel computation.
- Map is always north-up (confirmed by Milo across all sample screenshots).
  This is the load-bearing assumption; if DoorDash ever rotates the map,
  results will be wrong. Sanity checks catch gross errors.

**Alternatives rejected:**
1. OCR-based town detection from map labels
   - REJECTED: town labels on map are reference geography (shown for context),
     not the delivery town. Would produce systematically wrong answers.
2. Pure GPS-bearing estimator (current approach)
   - REJECTED: confirmed unreliable at low speeds (handover 2026-06-18).
3. MediaProjection + full-screen map capture + pin detection
   - REJECTED: poor UX (separate permission flow), explicitly avoided per
     FEATURE_BACKLOG.md. AccessibilityService.takeScreenshot() suffices.
4. Color-based HSV filtering for pin detection
   - REJECTED during 2026-06-19 analysis: driver pin uses soft muted blue
     with low saturation, indistinguishable from map water/features by
     color alone. Template matching is required for reliable detection.

**Approved by:** Milo (2026-06-19)

**Open risks:**
- Pin template stability across DoorDash app updates: templates extracted
  from 2026-06-18 screenshot. If DoorDash redesigns pin icons, templates
  must be re-extracted. Mitigation: log match scores to logcat; if scores
  drop below threshold consistently across offers, alert driver to update.
- Map north-up assumption: if DoorDash introduces map rotation during
  navigation, all results will be wrong. Sanity checks (50-mile radius
  from driver) catch gross errors but not subtle ones.
- Restaurant GPS geocoding accuracy: Nominatim geocode of restaurant name
  near driverGPS may return wrong location in dense areas with multiple
  same-name restaurants. Pre-existing issue, not introduced by this change.

**Implementation files:**
- app/src/main/java/com/augusteenterprise/giglens/town/PinDetectionTownEstimator.kt (NEW)
- app/src/main/res/drawable/pin_template_briefcase.png (NEW)
- app/src/main/res/drawable/pin_template_house.png (NEW)
- app/src/main/res/drawable/pin_template_driver.png (NEW)
- app/src/main/java/com/augusteenterprise/giglens/town/DeliveryTownEstimator.kt (MODIFIED — dispatch)
- app/src/main/java/com/augusteenterprise/giglens/data/AppConfig.kt (MODIFIED — new key)
- app/build.gradle.kts (MODIFIED — OpenCV dependency)

---

