# GigLens — Session Handover
**Date:** 2026-06-15
**Version at session end:** 0.1.145
**Build state:** PASSING
**Conducted by:** Claude (Anthropic) — manual session

---

## What Was Completed This Session

### 1. Bug fix — duplicate analytics entries
- File: OfferDetectorService.kt
- Root cause: extractOfferFromNodes() fired on every accessibility event while offer screen visible
- Fix: added offer fingerprint dedup guard (pay:distance hash, 30s window)
- New fields: lastOfferFingerprint, lastOfferBroadcastMs
- Constant: OFFER_DEDUP_WINDOW_MS = 30_000L

### 2. Bug fix — MediaProjection dialog still appearing
- File: MainActivity.kt
- Root cause: showAutoModeDialog() always called requestScreenCapturePermission() regardless of mode
- Fix: showAutoModeDialog() now reads saved mode from DB — accessibility/tap modes call startAccessibilityOnlyServices()
- New function: startAccessibilityOnlyServices()

### 3. Feature — tap-to-capture mode in Settings
- Files: SettingsActivity.kt, OfferDetectorService.kt
- Added "tap" as distinct saved mode (replaces "button")
- loadSettings() maps both "tap" and "button" to rbCaptureButton for backwards compat
- saveSettings() now saves "tap" for rbCaptureButton selection
- cachedAutoCapureMode default changed from "button" to "tap"

### 4. Bug fix — accessibility state not reflected in Settings after granting
- File: MainActivity.kt
- Root cause: pendingAccessibilityEnable block called showAutoModeDialog() which didn't update UI
- Fix: block now calls startAccessibilityOnlyServices() directly after accessibility granted

### 5. Bug fix — LIVE badge not showing in accessibility mode
- File: MainActivity.kt
- Root cause: updateUI() only checked ScreenCaptureService.isRunning — always false in accessibility mode
- Fix: captureActive now checks OfferOverlayService.isRunning OR ScreenCaptureService.isRunning
- Fix: startAccessibilityOnlyServices() calls updateUI() after 500ms delay

### 6. Map debug logging added
- File: OfferDetectorService.kt
- Added content description and view ID collection inside walk()
- Logs to Crashlytics and map_debug.log for post-shift analysis
- Goal: determine if DoorDash accessibility tree exposes delivery town/location data

### 7. Debug APK signing fixed
- File: app/build.gradle.kts
- Added debug build type with release keystore signing
- Eliminates signature conflict between sideloaded debug APK and Play Store release

### 8. deploy.sh improvements
- Auto version bump before every build
- Post-deploy instructions with validation checklist
- Sideload step included (skipped gracefully when Pixel not reachable)

### 9. Scoring logic — reviewed, redesign scoped (not yet implemented)
- Confirmed: wear/tear stays in cost calculation
- Decision: remove hardcoded ceilings and floors
- Decision: green/yellow/red verdict based on net profit dollar amount (driver configurable)
- Decision: pill shows net profit + color; score shown only on expand (Option C)
- Decision: 0-100 score kept as internal quality metric
- Phase 2 planned: Facebook sentiment batch job → restaurant reputation DB → feeds score

---

## What Was Left Incomplete

- Scoring redesign (Phase 1) — scoped, not implemented — ~3 hours dedicated session
- MediaProjection full removal — still pending dedicated 2-3 hour session
- Order archive view (day/week/month) — not started
- Chart query scope fix — still shows all sessions not just current

---

## Known Broken (do not ignore)

- Play Store install forces reinstall (not update) — app is unreviewed/unpublished
  → Data wipe on install until Google review is completed
- Chart shows ALL captured orders across sessions not just completed ones
- Sideload only works when Pixel is on same network as milo-dev
- Fastlane version nag — run `gem install fastlane` to silence

---

## Next Session — Start Here

1. Install v145 on Pixel via Play Store internal testing opt-in link
2. Validate v145 fixes:
   - Settings shows accessibility as enabled after granting (no manual toggle needed)
   - LIVE badge turns green immediately after toggle ON
   - No MediaProjection dialog appears
   - Analytics shows no duplicates after a shift
3. Pull map debug log after next shift:
   adb -s 10.0.0.110:<PORT> pull /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/map_debug.log ~/giglens/docs/map_debug.log
4. Start Phase 1 scoring redesign (3 hour session — do not start unless you have 3 hours)

---

## Phase 1 Scoring Redesign — Full Scope (Next Dedicated Session)

### Design decisions confirmed:
- No hardcoded ceiling on net value or $/mile — open-ended scaling
- No hardcoded floor — driver sets minimums in Settings
- Verdict: GREEN/YELLOW/RED based on net profit dollar amount
- Pill display: net profit (e.g. $6.42) + color — instant glance
- Expanded sheet (MINI/FULL): score, cost breakdown, $/mile, minutes on job
- 0-100 score kept as internal quality metric (net value 70% + $/mile 30%)
- Default thresholds pre-populated so app works out of the box

### Files to change (5 files, ~3 hours):
1. ScorerConfigKeys.kt — add GREEN_PROFIT_THRESHOLD, YELLOW_PROFIT_THRESHOLD, floor keys
2. OfferScorer.kt — remove netValueMax/truePerMileMax hardcoding, GREEN/YELLOW/RED verdict
3. SettingsActivity.kt + activity_settings.xml — add threshold input fields with defaults
4. OfferOverlayService.kt — pill color from verdict, show net profit amount not score

### Default values to use:
- Green threshold: net profit ≥ $8.00
- Yellow threshold: net profit ≥ $4.00
- Red: below yellow threshold
- Floor pay/mile: $1.50 (driver configurable, used for score only not verdict)
- Floor total pay: $6.00 (driver configurable, used for score only not verdict)

### Phase 2 (future paid feature):
- Nightly batch on milo-dev: FB driver group scraper → Ollama sentiment → restaurant reputation SQLite DB
- At offer time: restaurant name lookup → cached sentiment score → folds into 0-100 composite
- Monetization: Phase 2 features behind paywall as app matures

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated on real shift |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | pay, distance, restaurant, countdown extracted |
| Offer dedup guard | ✅ Fixed | 30s fingerprint window — v145 |
| Camera button morph | ✅ Working | Validated on shift |
| Countdown timer on pill | ✅ Working | Fixed prior session |
| Pill screen targeting | ✅ Fixed | v139 |
| MediaProjection dialog suppression | ✅ Fixed | No dialog for accessibility/tap modes |
| Tap-to-capture mode | ✅ Added | Saved as "tap" in Settings |
| LIVE badge in accessibility mode | ✅ Fixed | v145 |
| Accessibility state in Settings | ✅ Fixed | v145 |
| Map debug logging | ✅ Added | Pulls content descs + view IDs post-shift |
| Debug APK signing | ✅ Fixed | Uses release keystore |
| Scoring redesign (Phase 1) | 🔲 Scoped | Green/yellow/red profit verdict — next dedicated session |
| Order charting | ⚠️ Partial | Scope incorrect — shows all sessions |
| Order archive (day/week/month) | 🔲 Not started | |
| ScreenCaptureService removal | 🔲 Planned | Dedicated 2-3 hour session |
| Play Store review submission | 🔲 Pending | Submit when testing complete |
| FB sentiment batch (Phase 2) | 🔲 Planned | Paid feature, future session |

---

## Decisions Made This Session

1. Toggle ON flow is fully automatic — reads saved mode from Settings DB, no dialogs
2. "tap" replaces "button" as the saved mode value for tap-to-capture
3. Debug APK must be signed with release keystore to avoid conflicts
4. deploy.sh auto-bumps version before every build
5. Scoring verdict will be GREEN/YELLOW/RED based on net profit, driver-configurable thresholds
6. Pill shows net profit + color; score shown only on expand (Option C)
7. 0-100 score kept as internal quality metric
8. Phase 2 sentiment is a batch process — nightly on milo-dev, cached lookup at offer time
9. Phase 2 is a paid feature — monetization path confirmed

---

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL (10.0.0.110:PORT): check Wireless Debugging each session — port changes on reboot
- Samsung S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Keystore: ~/giglens/giglens-release.keystore
- Play Store JSON key: ~/giglens/google-play-api-key.json (DO NOT COMMIT)
- Ruby: rbenv 3.4.9 at ~/.rbenv/versions/3.4.9
- Fastlane: 2.236.0 (run `gem install fastlane` to upgrade to 2.236.1)

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
- app/src/main/java/com/augusteenterprise/giglens/ui/MainActivity.kt
- app/src/main/java/com/augusteenterprise/giglens/ui/SettingsActivity.kt
- app/build.gradle.kts
- deploy.sh

---

## Features Left to Implement (Priority Ranked)

### High Priority
1. **Scoring Phase 1** — GREEN/YELLOW/RED profit verdict, configurable thresholds, pill shows net profit — 3 hour session
2. **Validate v145 fixes** — accessibility state, LIVE badge, dedup, no MediaProjection dialog
3. **Map debug log review** — pull after next shift, determine if town data accessible
4. **ScreenCaptureService removal** — dedicated 2-3 hour session

### Medium Priority
5. **Order archive view** — day/week/month grouping + fix chart scope
6. **Delivery town estimation** — reverse geocode restaurant → estimate drop-off zone
7. **Fastlane Ruby fix** — Gemfile, wire rbenv 3.4.9
8. **In-app update prompt** — notify driver before shift if update available
9. **Countdown UX improvement** — needs better urgency signal
10. **Play Store review submission** — store listing, screenshots, content rating, privacy policy

### Low Priority / Tabled
11. **FB sentiment batch (Phase 2)** — nightly scraper + Ollama + reputation DB
12. **Robolectric/Espresso testing** — tabled
13. **ConnectionRecord leaks** — ~25 dead entries, low impact

---

*Next developer: read SESSION_PROTOCOL.md first, then return here.*

## Addendum — Pill Header Location Placeholder

Pill header must reserve space for delivery town estimation (future paid feature).
Phase 1: show static placeholder (📍 ---) in pill header alongside net profit.
Phase 2: replace placeholder with estimated town name from reverse geocode or accessibility tree.

Pill header layout (Phase 1):
  [$6.42]  •  [📍 ---]
  green/yellow/red background

Pill header layout (Phase 2 paid):
  [$6.42]  •  [📍 Cherry Hill]

Design rule: pill width must accommodate ~15 char town name without reflow.

## Addendum — Delivery Town Estimation Strategy

### Goal
Determine the city/town where the offer will be delivered — shown in pill header.
NOT the pickup/restaurant location. The DELIVERY destination town.

### What we have at offer time
- Pay amount
- Total distance (driver → pickup → dropoff — DoorDash includes full trip)
- Restaurant name + implicit pickup location
- Deliver by time
- Countdown seconds
- Map debug log (content descriptions + view IDs) — being collected as of v145

### Investigation priority (next session — do this FIRST)
1. Pull map_debug.log after next shift:
   adb -s 10.0.0.110:<PORT> pull /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/map_debug.log ~/giglens/docs/map_debug.log
2. Inspect log for any of these in content descriptions or view IDs:
   - Lat/lng coordinates
   - Address fragments
   - Town/city names
   - Map tile URLs with coordinates
   - Any delivery destination hint
3. If found → reverse geocode → town name (free Nominatim/OpenStreetMap API, no key needed)
4. If NOT found → fall back to estimation approach below

### Estimation fallback (if accessibility tree exposes nothing)
- Geocode restaurant name → pickup coordinates (Google Geocoding or Nominatim)
- Estimate pickup leg distance (driver → restaurant, typically 1-2mi)
- Delivery leg = total distance - pickup leg
- Draw radius from restaurant coordinates using delivery leg distance
- Reverse geocode likely delivery zone → town name
- Display as approximate: "📍 ~Cherry Hill"

### Pill display
- Confirmed town (from accessibility tree): "📍 Cherry Hill"
- Estimated town (from fallback): "📍 ~Cherry Hill"
- Unknown (Phase 1 placeholder): "📍 ---"

### Tiered feature model
- Tier 1 (Free): "📍 ---" — placeholder only
- Tier 2 (Standard paid): delivery town shown — hosted cloud lookup
- Tier 3 (Self-hosted paid): driver runs sentiment + geocode DB on own desktop
  → GigLens queries via Telegram bot → local DB responds with town + sentiment
  → No dependency on Milo's infrastructure

### Telegram bot approach (Tier 3)
- Driver installs lightweight Python bot on their desktop (~50 lines)
- Bot listens for restaurant name + distance query from GigLens
- Queries local sentiment/geocode DB → returns town + reputation score
- GigLens receives response and populates pill
- Free Telegram Bot API — no WhatsApp business account needed

### Why NOT pickup location
DoorDash hides the delivery address. Restaurant = pickup, not delivery.
Town estimation must be based on distance + direction from restaurant,
or from accessibility tree map data if exposed.

## Addendum — Voice/Hands-Free Feature

### Goal
Read offer data aloud when pill appears — driver keeps eyes on road.

### Spoken format
"$8.42 net. 6.2 miles. Cherry Hill. Green offer."
"$4.10 net. 8.5 miles. Destination unknown. Yellow offer."

### Technology
- Android TextToSpeech API — built into Android SDK
- No external dependency, no cost, works fully offline
- No new libraries needed

### Settings options to add
- Voice enabled/disabled toggle (default OFF)
- Speech rate — slow/normal/fast
- Individual toggles:
  - Read net profit (default ON)
  - Read distance (default ON)
  - Read verdict color (default ON)
  - Read estimated town (default ON — says "destination unknown" if unavailable)

### Tiered model
- Tier 1 (Free): profit + distance + color read aloud
- Tier 2/3 (Paid): town name included in voice when available

### Implementation notes
- Trigger: when offer pill appears (same event that morphs camera button)
- File to modify: OfferOverlayService.kt — add TTS init in onCreate(), speak() on offer received
- Respect Android DND and driving mode
- Cancel speech if offer expires or pill dismissed
- Estimated effort: 1-2 hours, single file change

### Phase 1 priority
Add alongside scoring redesign — same session, low risk, no new dependencies.
Total revised Phase 1 estimate: ~4.5 hours
