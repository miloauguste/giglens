# GigLens — Feature Backlog
**Last updated:** 2026-06-26 (v0.1.259)

---

## ✅ Completed 2026-06-26 Session (v0.1.257 → v0.1.259) — branch `feat/driver-anchored-ab`

| Feature | Status | Notes |
|---------|--------|-------|
| Dynamic map-band crop | ✅ Built | `OfferDetectorService.detectMapBand()` — fixes driver dot cropped out of frame on tall maps; offline-validated 2/8→8/8 dot recovery — v259 |
| Mapbox attribution filter | ✅ Built | `PinDetector` MAPBOX_ZONE — bottom-left exclusion kills false "mapbox" logo pin — v259 |
| Driver-anchored town A/B (shadow) | ✅ Built | Projects dropoff from known driver GPS (Spain-proof); coords logged in rawOcrText for offline scoring; primary unchanged — v259 |
| Standing rule: testing CTAs | ✅ Added | WORKING_AGREEMENTS.md — every wrap-up/handover must enumerate testing needed |

**⚠️ NOT YET VALIDATED IN THE WILD** — branch not merged. Next shift must collect
confirmation taps + verify dynamic crop + A/B logging. See `docs/LAST_SESSION_20260626.md`
"TESTING NEEDED / CALLS TO ACTION" for the exact checklist. Post-shift: score
driver-anchored vs restaurant-anchored against `confirmedTown`, decide primary.

---

## Phase 1 — Next Dedicated Session (~4.5 hours)

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 1 | **Scoring redesign** | GREEN/YELLOW/RED profit verdict, configurable thresholds in Settings, no hardcoded ceiling | 1.5hr | Free |
| 2 | **Pill header polish** | Refine net profit + town layout once accuracy data confirms approach | 30min | Free |
| 3 | **Voice/hands-free** | Android TTS reads offer aloud on pill appear — profit, distance, color, town | 1.5hr | Free/Paid |

---

## ✅ Completed 2026-06-20 Session (v0.1.192 → v0.1.205)

| Feature | Status | Notes |
|---------|--------|-------|
| PinDetector.kt | ✅ Built | Pure Kotlin HSV blob detection — `town/PinDetector.kt` |
| PinDetectionTownEstimator.kt | ✅ Built | Pixel calibration → bearing → Nominatim — `town/PinDetectionTownEstimator.kt` |
| PIN_DETECTION_ENABLED config key | ✅ Built | Seeded via `seedIfAbsent()` on service connect — `AppConfig.kt` |
| `seedIfAbsent()` DAO method | ✅ Built | INSERT OR IGNORE — safe upgrade path for new config keys — `AppConfigDao.kt` |
| Pin detection wired into screenshot flow | ✅ Built | `testTakeScreenshot()` onSuccess calls `PinDetector.detect()` |
| DeliveryTownEstimator pin dispatch | ✅ Built | pin_detection → partial_pin → GPS fallback (GPS fallback later replaced, see June 21) |
| Pin Detection toggle in Settings | ✅ Built | `switchPinDetectionEnabled`, loads/saves from DB — `SettingsActivity.kt` |
| Pill revert timer bug | ✅ Fixed | `coerceAtMost(30)` → `coerceIn(5, 300)` — full 5–300s range now honored |
| Settings page design | ✅ Rebuilt | Matches main page — 3 contrast passes, all text visible — `activity_settings.xml` |
| Dark mode | ✅ Built | Persistent via SharedPreferences, full color token system, `values-night/colors.xml` |
| Dedup window fix (partial) | ✅ Partial | `AccessibilityOfferReceiver.DEDUP_WINDOW_MS` 30s→120s; `OfferDetectorService` still 30s |

## ✅ Completed 2026-06-21 Session (v0.1.206 → v0.1.211)

| Feature | Status | Notes |
|---------|--------|-------|
| Dedup window fix (complete) | ✅ Fixed | `OfferDetectorService.OFFER_DEDUP_WINDOW_MS` 30s→120s — both constants now 2min |
| Town bearing overhaul (3-tier) | ✅ Built | Full pin → partial pin → unavailable — no GPS bearing fallback; stale result cleared on entry |
| Pill heading town | ✅ Built (Pro) | `+$X.XX 45s · 📍 Burlington` inline, teal #00C9A7 80% size — `pillTextWithTown()` |
| Play Store internal testing deploy | ✅ Deployed | v0.1.211 on internal track + Pixel sideload |

## ✅ Completed 2026-06-24 Session (v0.1.253 → v0.1.254)

| Feature | Status | Notes |
|---------|--------|-------|
| GPS warning strip (main screen) | ✅ Built | Amber strip in onResume; FIX button → app settings; auto-hides on grant — v254 |
| Pill "📍 no GPS" signal | ✅ Built | Overrides "📍 ---" when permission is specific cause — v254 |
| Global restaurant geocoding blocked | ✅ Fixed | `resolveByCity` returns null when no city context; prevents Spain-class errors — v254 |

## ✅ Completed 2026-06-23 Session (v0.1.251 → v0.1.252)

| Feature | Status | Notes |
|---------|--------|-------|
| CSV export (Offer History) | ✅ Built | All-time history → share sheet; excludes location fields — v252 |
| CSV import (Offer History) | ✅ Built | File picker, timestamp dedup, merge — v252 |
| FileProvider | ✅ Added | Required for cache-dir file sharing — v252 |
| deploy.sh signature mismatch guard | ✅ Fixed | Detects Play App Signing key conflict, prompts before DB wipe — v1.3 |

## ✅ Completed 2026-06-22 Session (v0.1.212 → v0.1.216)

| Feature | Status | Notes |
|---------|--------|-------|
| Retail pickup restaurant (Wawa, 7-Eleven) | ✅ Fixed | `extractOfferFromNodes()` + `extractRestaurant()` now match "Retail pickup"; digit-start allowed for "7-Eleven" — v215 |
| Town truncation in pill | ✅ Fixed | `abbreviateTown()` strips Township/Borough/County suffixes, caps at 14 chars — v215 |
| `BLOB_MIN_PX` 200→50 | ✅ Fixed | Handles driver dot in zoomed-out maps (~8px min diameter) — Taco Bell case — v215 |
| Screenshot noise reduction (bitmap crop) | ✅ Built | PinDetector receives y=8%–32% crop only (map region) — excludes notification banner and offer text — v216 |
| Pill hidden during screenshot | ✅ Built | `alpha=0` before shot, restored in onSuccess/onFailure/catch — v216 |
| `zoom=10` → `zoom=12` (both estimators) | ✅ Fixed | Borough/village level from Nominatim — fixes Pemberton/Southampton class of error — v216 |
| Short-trip confidence guard | ✅ Built | pickup→dropoff < 80px → confidence="medium"; logcat warning fires — v216 |

## ✅ Completed This Session (v154)

| Feature | Status | Notes |
|---------|--------|-------|
| Delivery town estimation | ✅ Built | GPS + Nominatim geocode + bearing projection — see DeliveryTownEstimator.kt |
| Town accuracy tracking | ✅ Built | Yes/No notification after offer, DB columns added (migration 7→8) |
| Pill shows town | ✅ Added | "$8.42 📍 ~Cherry Hill" — PILL/MINI/FULL states |
| Receiver-level dedup guard | ✅ Fixed | Survives OfferDetectorService restarts |
| deploy.sh port persistence | ✅ Added | .pixel_port file, only prompts on connection failure |

---

## 🔬 Decision Point — Delivery Town Accuracy (COLLECTING DATA)

**Pin-based CV estimation is now built and wired (v0.1.194+).** The GPS-bearing fallback is permanently removed as of v0.1.208. The 3-tier estimation logic is: full pin → partial pin → unavailable.

**Next milestone: collect Yes/No confirmations from real shifts.** Run this after 5-10 shifts with confirmations logged:
```sql
SELECT estimatedTownMethod,
  COUNT(*) as total,
  SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) as correct,
  ROUND(100.0 * SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) / COUNT(*), 1) as accuracy_pct
FROM offer_captures
WHERE estimatedTown IS NOT NULL AND townAccurate IS NOT NULL
GROUP BY estimatedTownMethod;
```

- **`pin_detection` >80%** → algorithm confirmed. Keep as-is.
- **`partial_pin` >80%** → partial-pin path also solid.
- **Either path <80%** → investigate short-trip confidence guard (< 80px pin separation → "medium") and whether "medium" results should be suppressed to "unavailable".
- **Most rows `unavailable`** → PinDetector threshold tuning needed; check HSV blob detection vs. actual screenshot samples. Note: `BLOB_MIN_PX` lowered to 50 (v215) and map cropped to 8–32% of screen (v216) — may improve detection rate.

**Status as of 2026-06-22:** 0 confirmations logged — `townAccurate` null on all rows. Driver has not tapped Yes/No on any town notification across any shift. Data collection must begin next shift. Note: `zoom=12` fix (v216) may already fix the Southampton/Pemberton class of error — first confirmation tap will verify.

**Confirmed dead ends (do not re-investigate):**
- DoorDash accessibility tree exposes NO coordinates, addresses, or map tile URLs
- Content descriptions on map view are empty
- Text between restaurant name and "customer dropoff" label contains nothing useful
- Idle-screen zone text ("nj: moorestown/mt. laurel") is driver's ASSIGNED zone, not delivery zone
- GPS bearing (device sensor) — unreliable when stationary; removed from all estimation paths as of v0.1.208
- Driver→restaurant coordinate bearing — wrong direction assumption; customer is NOT necessarily along that line

---

## Phase 2 — Paid Features

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 5 | **Town estimation accuracy data** | Collect Yes/No confirmations across 5-10 shifts — run accuracy SQL query when sufficient rows collected | 0hr | Free |
| 6 | **FB sentiment batch** | Nightly scraper + Ollama sentiment → restaurant reputation DB. Tier 2: hosted cloud (Supabase/Railway). Tier 3: self-hosted via Telegram bot | 6-8hr | Tier 2/3 |
| 7 | **Sentiment score in pill** | Restaurant reputation feeds into 0-100 quality score shown on expand | 2hr | Tier 2/3 |
| 8 | **PDF shift report** | Formatted export with date range filter | 2-3hr | Tier 2/3 |
| 9 | **Google Sheets sync** | Direct analytics export to Google Sheets | 3-4hr | Tier 2/3 |
| 10 | **Telegram bot (Tier 3)** | Driver runs sentiment + geocode DB on own desktop, GigLens queries via Telegram bot | 4-5hr | Tier 3 |

---

## Infrastructure / Tech Debt

| # | Feature | Description | Effort | Priority |
|---|---------|-------------|--------|----------|
| 11 | **ScreenCaptureService removal** | Full removal — touches 5 files + manifest. TODO markers already in place | 2-3hr | High |
| 12 | **Order archive view** | Day/week/month grouping + fix chart scope (currently shows all sessions) | 3-4hr | High |
| 13 | **Play Store review submission** | Store listing, screenshots, content rating, privacy policy | 2hr | High |
| 14 | **Fastlane Ruby fix** | Gemfile, wire rbenv 3.4.9 into deploy path — currently shows version nag | 30min | Medium |
| 15 | **In-app update prompt** | Notify driver before shift if update available | 1-2hr | Medium |
| 16 | **Countdown UX improvement** | Needs better urgency signal — driver feedback from shift | 1hr | Medium |
| 17 | **Zone text extraction from idle screen** | Only if needed to disambiguate multiple same-name restaurants — deferred until accuracy data shows necessity | 1hr | Low |

---

## Tiered Monetization Model

| Feature | Tier 1 Free | Tier 2 Standard | Tier 3 Self-Hosted |
|---------|:-----------:|:---------------:|:-----------------:|
| Color pill + net profit | ✅ | ✅ | ✅ |
| Delivery town estimate (GPS-based) | ✅ | ✅ | ✅ |
| Voice readout (profit + distance + color + town) | ✅ | ✅ | ✅ |
| CSV export (current shift) | ✅ | ✅ | ✅ |
| High-confidence town (pin-based CV, built v0.1.194) | ✅ | ✅ | ✅ |
| Restaurant sentiment score | ❌ | ✅ | ✅ |
| PDF shift report + date range | ❌ | ✅ | ✅ |
| Google Sheets sync | ❌ | ✅ | ✅ |
| Self-hosted DB via Telegram bot | ❌ | ❌ | ✅ |

*Note: delivery town moved to Free tier since GPS-based estimation requires no
ongoing infrastructure cost — only the higher-confidence map-based version
(if ever built) would be paid.*

---

## Tabled / Low Priority

| # | Feature | Reason tabled |
|---|---------|---------------|
| T1 | Robolectric/Espresso automated testing | Too early — app still in active development |
| T2 | Android 14 partial screen sharing | Irrelevant after ScreenCaptureService removal |
| T3 | ConnectionRecord leaks | ~25 dead entries, low impact, no user-facing effect |

---

## Implementation Notes

### Delivery Town — Algorithm (Implemented v154)
Geocode restaurant name near driver GPS (Nominatim, Option 1 default)

→ pickup coordinates
Pickup leg = straight-line distance(driver GPS → restaurant)
Delivery leg = total distance - pickup leg
Project delivery leg from restaurant using driver's GPS bearing
Reverse geocode projected point → city/town name
Display: "📍 ~Cherry Hill" (tilde = estimated, not confirmed)
### Voice Feature — Spoken Format (Not Yet Built)
"$8.42 net. 6.2 miles. Cherry Hill. Green offer."

"$4.10 net. 8.5 miles. Destination unknown. Yellow offer."

### Phase 2 Architecture (Production, Not Yet Built)
Nightly batch (milo-dev or cloud cron)

FB scraper → Ollama sentiment → pushes to cloud DB (Supabase)
Phone at offer time

restaurant name → HTTPS API → cloud DB → sentiment score returned
Tier 3 self-hosted

restaurant name → Telegram bot → driver's local DB → score returned
---

*Update this file at the start of every session AND at handover. Move completed
items into the "Completed This Session" section, then fold into LAST_SESSION.md
on the next handover so this file always reflects only what's still pending.*

---

## Sentiment Agent — Separate Repository (Planned)

**Decision:** Sentiment Agent is a standalone project, own git repo, NOT part of
GigLens or Auguste CRM. Reason: independent deployment lifecycle — runs on
milo-dev now, may move to cloud hosting (Supabase/Railway) later, and is shared
infrastructure that could theoretically serve other apps beyond GigLens.

### Proposed structure
~/sentiment_agent/          (new git repo)

├── scraper.py               # Playwright — scrapes FB driver groups

├── sentiment.py              # Ollama llama3.1:8b — scores comments

├── aggregator.py             # Aggregates scores per restaurant → reputation DB

├── reputation.db             # SQLite — restaurant_name, sentiment_score, sample_count, last_updated

├── run_agent.sh              # Orchestrates pipeline, runs via cron

├── requirements.txt

├── .gitignore

├── README.md

└── docs/

└── LAST_SESSION.md       # Same handover pattern as GigLens/Auguste CRM

### Pipeline

scraper.py    — Playwright logs into FB, scrapes target driver groups → raw_comments table
sentiment.py  — reads unprocessed comments, Ollama scores each → sentiment_scores table
aggregator.py — groups by restaurant name, computes avg sentiment + confidence → reputation table
reputation.db — ready for GigLens (direct read now, API/Telegram bridge later)


### Connection to GigLens (current state: not built)
- **Now (home server):** GigLens cannot reach reputation.db when phone is remote from milo-dev
- **Tier 3 self-hosted (future):** Telegram bot bridge — GigLens queries bot, bot reads local DB, responds
- **Tier 2 hosted (future):** reputation.db synced to cloud Postgres (Supabase), GigLens calls REST API directly

### Effort estimate
- Repo setup + scraper.py (reuse Auguste CRM FB scout patterns): 2-3hr
- sentiment.py (Ollama integration): 1-2hr
- aggregator.py + reputation.db schema: 1hr
- Testing end-to-end on a few real restaurants: 1-2hr
- **Total: ~6-8hr** — dedicated session, not a quick add-on

### Status
🔲 Not started — repo not yet created. Scope this when ready to begin Phase 2
sentiment work (after delivery town accuracy data collection is further along).

---

## Registration + Billing — Local Onboarding with Stripe (Planned)

**Decision summary:**
- Registration is skippable — app usable with limited features until paid
- Billing via Stripe Checkout (hosted page, not custom form)
- Registration data (name/alias, email) stored locally in SQLCipher-encrypted
  Room database — NOT plaintext
- Paid/tier status CANNOT be determined by the phone alone — requires a minimal
  backend relay to Stripe (phone cannot hold Stripe secret key)

### Why a backend is required (not optional)
Stripe's secret key must never ship inside an APK — it would let anyone extract
it via decompilation and create/refund charges arbitrarily. Stripe Checkout still
needs a server to (1) create the checkout session, (2) receive the webhook
confirming payment, (3) let the phone confirm "tier=paid" status. This is true
even though Stripe Checkout itself is hosted by Stripe — only the keys must
never touch the device.

### Architecture
Phone (SQLCipher-encrypted Room DB)

→ name/alias, email, tier flag (free/paid), stripe_customer_id
Minimal backend (FastAPI on milo-dev now, Railway/Render later)

→ POST /create-checkout-session  → returns Stripe Checkout URL

→ POST /webhook (Stripe)          → receives payment success event

→ GET  /tier-status?email=X       → phone polls/confirms tier after checkout

### Part 1 — Local registration UI + SQLCipher migration
- Add `net.zetetic:android-database-sqlcipher` dependency
- Migrate OfferDatabase to SQLCipher-backed Room (passphrase from Android Keystore)
- New RegistrationActivity: name/alias, email fields, "Skip for now" option
- New Room entity: UserProfile (id, alias, email, tier, stripeCustomerId, createdAt)
- Settings: show current tier (Free/Paid), "Upgrade" button if free
- Effort: ~3-4hr (SQLCipher migration touches existing DB, needs careful testing
  with existing offer_captures data — migration must not corrupt history)

### Part 2 — Minimal Stripe backend
- New FastAPI app on milo-dev (separate from sentiment_agent, separate from
  Auguste CRM — likely its own small repo or folder)
- Endpoints: create-checkout-session, stripe webhook receiver, tier-status check
- Stripe test mode first, switch to live mode before public launch
- Effort: ~3-4hr (FastAPI + Stripe SDK — you already know FastAPI from Auguste CRM)

### Part 3 — Wire registration to feature gating
- Free tier: color pill + net profit + GPS-based town estimate (per current
  monetization model in this doc)
- Paid tier: unlocks score detail on expand, voice readout, future sentiment
  features, etc.
- GigLens checks local `tier` flag before rendering gated features
- Effort: ~1-2hr (mostly conditionals in existing UI code)

### Total estimate: ~8-10hr across 2-3 dedicated sessions
Recommend splitting: Session A = SQLCipher migration + registration UI,
Session B = Stripe backend, Session C = feature gating + end-to-end test.

### Open questions for next session
- Repo location for Stripe backend — own repo (consistent with Sentiment Agent
  decision) or folder inside an existing project?
- Stripe test vs live mode timeline — when do we need real payments working?
- What exactly is "limited usage" for free tier — time-limited trial, offer-count
  limited, or feature-limited (current plan)?

### Status
🔲 Not started — scoped only. Do not begin until Phase 1 scoring redesign and
delivery town accuracy validation are further along, per priority order.

---

## Accessibility Permission Disclosure Screen (Planned — rebuild with ViewBinding)

**Background:** An earlier session (likely DeepSeek-generated via Project Autonomous)
created AccessibilityDisclosureScreen.kt + AccessibilityDisclosureActivity.kt using
Jetpack Compose — a UI framework never configured in this project (GigLens uses
ViewBinding/XML exclusively). These files were untracked, never wired into any
navigation flow, and silently broke every clean build since their creation because
the Compose Gradle plugin was never added. DELETED on 2026-06-17 to unblock builds.

**The underlying idea was good and should be rebuilt properly:**
A disclosure screen shown to the driver BEFORE requesting the Android Accessibility
Service permission, explaining in plain language:
- What GigLens reads (only on-screen text while DoorDash is foregrounded)
- What GigLens does with it (on-device scoring, nothing sent anywhere unless
  cloud sentiment features are explicitly enabled)
- What GigLens never does (never taps/accepts/declines on driver's behalf)
- How to turn it off (Settings > Scan Mode, or revoke in Android Settings)

**Why this matters:** Google Play reviews accessibility-service apps closely (the
same API can be abused by stalkerware/banking trojans, as confirmed via research
this session on takeScreenshot()). A clear, honest disclosure screen shown at the
right moment is good practice and likely required for smooth Play Store approval.

**Rebuild plan:**
- New XML layout: activity_accessibility_disclosure.xml (ViewBinding, NOT Compose)
- New AccessibilityDisclosureActivity.kt using existing ViewBinding pattern
- Trigger point: shown once, before first accessibility permission request in
  MainActivity's onboarding flow (showCaptureOnboardingFlow() → showAccessibilityDialog())
- Content: reuse the well-written disclosure text from the deleted Compose version
  (4 sections: what's read, what's done with it, what's never done, your control)
- Effort: ~1-2hr (straightforward ViewBinding screen, content already drafted)

**Status:** 🔲 Not started — content already written (see git history before
2026-06-17 deletion if needed for reference), just needs ViewBinding implementation.


## ⚠️ Pre-Launch Review Needed — AUTO_CAPTURE_MODE Default Changed to "accessibility"

**What changed (2026-06-17 session):** Seed default for `AUTO_CAPTURE_MODE`
config key changed from `"off"` to `"accessibility"` (fully automatic,
no-tap capture mode) in `AppConfig.kt`, plus aligned the matching
null-fallback in `OfferDetectorService.kt` to the same value. This was done
as a developer convenience to speed up real-shift validation testing — Milo
wanted Settings to default to Fully Automatic rather than Off, to avoid
manually toggling it after every fresh install during this dev/test phase.

**Why this needs reconsideration before Play Store release:** A default-on
automatic accessibility-capture mode is a stronger behavioral claim than an
opt-in one, and interacts directly with the still-unbuilt Accessibility
Disclosure Screen (see separate backlog item above). Google Play's review of
accessibility-service apps scrutinizes default-on capture behavior more than
user-initiated capture. Before public release, explicitly decide:
- Should a fresh install for a real driver default to "accessibility" (current
  state) or "off" (original default, requires driver to opt in via Settings)?
- Does the disclosure screen's "how to turn it off" language need to instead
  say "how to turn it on," given the new default?
- Does this change anything about what's stated in the Play Console
  accessibility-service declaration or demo video (both already drafted per
  earlier session notes)?

**Status:** 🔲 Not decided — currently shipping as "accessibility" default in
the debug/internal-testing build. Must be explicitly revisited as part of
Play Store review submission prep (see item #13 above), not left as an
accidental holdover from dev convenience.

---

## Updated: 2026-06-19 — Post-session sync

### ✅ Completed 2026-06-20 + 2026-06-21 (see sections above for full detail)
- PinDetector.kt + PinDetectionTownEstimator.kt — built and wired
- 3-tier town estimation: full pin → partial pin → unavailable (no wrong projections)
- Settings page redesign + dark mode
- Dedup window extended to 2 minutes in both services
- Town in pill heading (Pro feature, code in place, gate not yet wired)
- Deployed to Play Store internal track (v0.1.211)

### 🔲 Immediate Next — Town Accuracy Validation
1. **Tap Yes/No on town confirmation notifications during next shift** — `townAccurate` is null on all rows; accuracy SQL query cannot run without confirmation data
2. **Check logcat for short-trip warnings** — `adb logcat -d | grep "short trip"` — if > 30% of offers hit the < 80px guard, consider returning "unavailable" for short trips instead of "medium"
3. **Verify zoom=12 fixed Pemberton class of error** — first Yes/No confirmation on a township-area offer will tell us if the borough-level naming is now correct

### 🔲 Queued (priority order)
4. Phase 1 scoring redesign (GREEN/YELLOW/RED configurable thresholds) — **next dedicated feature work**
5. Decide AUTO_CAPTURE_MODE default for real drivers (pre-launch review)
6. Accessibility disclosure screen rebuild (ViewBinding)
7. Pro feature gate for pill heading town (gate logic not yet wired — currently always shows)
8. Registration + Stripe billing (SQLCipher + FastAPI backend)
9. Sentiment Agent repo (separate project)
10. Order archive view (day/week/month grouping)
11. Play Store review submission (store listing, screenshots, content rating, privacy policy)
12. ScreenCaptureService removal (5 files + manifest, TODO markers in place)
13. Debug email pipeline validation (DebugOfferEmailer — still unvalidated end-to-end)

### ⚠️ Pre-Launch Review Required (must decide before public release)
- AUTO_CAPTURE_MODE default set to "accessibility" for dev convenience —
  must explicitly decide whether this is right for real drivers before shipping
- Accessibility disclosure screen content must reflect default-on capture mode
- Pro gate: `pillTextWithTown()` shows town unconditionally — subscription check
  must be wired before public launch
- google-play-api-key.json stripped from git history (2026-06-18) — confirm
  no other credential exposure remains before public launch

### 🔲 Tabled (not forgotten, just not urgent)
- Robolectric/Espresso automated testing (T1) — too early, app still in flux
- Town accuracy cross-reference via Nominatim full address components —
  deferred until CV estimator accuracy data collected
- Dependabot alert handling workflow — documented in 2026-06-18 handover,
  monitor Security tab weekly
