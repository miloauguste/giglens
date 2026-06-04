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
