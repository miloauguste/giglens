# GigLens — Session Handover
**Date:** 2026-06-15
**Version at session end:** 0.1.154
**Build state:** PASSING
**Conducted by:** Claude (Anthropic) — manual session

---

## What Was Completed This Session

### 1. Bug fix — duplicate analytics entries (receiver-level dedup)
- File: AccessibilityOfferReceiver.kt
- Root cause: dedup guard in OfferDetectorService reset on service restart, allowing duplicates
- Fix: added companion-object dedup guard directly in receiver (survives service restarts)
- New fields: lastFingerprint, lastInsertMs, DEDUP_WINDOW_MS = 30_000L

### 2. deploy.sh — saved Pixel port + post-deploy instructions
- File: deploy.sh
- Port now saved to .pixel_port, only prompts on connection failure
- Auto version bump before every build (empty git commit)
- Post-deploy instructions printed with Play Console link + validation checklist

### 3. Delivery town estimation — built from scratch
- New file: geocoding/DeliveryTownEstimator.kt
- Algorithm: geocode restaurant name near driver GPS (Nominatim, Option 1 default) →
  pickup coordinates → delivery leg = total distance - pickup leg →
  project from pickup using driver bearing → reverse geocode → town name
- Option 2 (city-based search) also implemented as fallback/driver choice
- Investigated map accessibility tree exposure — confirmed DoorDash exposes NO
  coordinates, NO addresses, NO map tile URLs. Only restaurant name + "customer
  dropoff" label. Zone text ("nj: moorestown/mt. laurel") visible on idle screen
  but represents driver's assigned zone, not delivery zone — used only as
  Nominatim search anchor, not as delivery estimate.
- Considered and rejected: map screenshot + pixel pin detection (would require
  MediaProjection — explicitly avoided per product decision, poor UX)

### 4. Pill + expanded sheet — delivery town display
- File: OfferOverlayService.kt
- Added EXTRA_DELIVERY_TOWN constant
- Pill (PILL state): shows "$8.42  📍 ~Cherry Hill  25s" via netLabelSpannable()
- MINI state: town shown in teal below restaurant name
- FULL state: town shown below restaurant name + as "Delivery town" detail row

### 5. Town estimation wired into offer pipeline
- File: AccessibilityOfferReceiver.kt
- Added LocationHelper.getCurrentLocation() call before scoring
- Added DeliveryTownEstimator.estimateTown() call using restaurant + distance + GPS
- driverLat/driverLon now populated from real GPS (previously hardcoded null)
- Town estimate passed to overlay via EXTRA_DELIVERY_TOWN

### 6. Accuracy tracking system — built for A/B testing town estimation
- DB migration 7→8: added estimatedTown, estimatedTownMethod, confirmedTown,
  townAccurate columns to offer_captures
- New file: TownAccuracyReceiver.kt — handles Yes/No notification taps
- New DAO method: updateTownAccuracy(captureId, confirmedTown, accurate)
- Post-offer notification: "Delivering to ~Cherry Hill? ✅ Yes / ❌ No"
- Manifest: registered TownAccuracyReceiver for TOWN_CONFIRMED/TOWN_WRONG actions
- Goal: collect real-world accuracy % before deciding if MediaProjection-based
  map screenshot approach is needed for paid tier

---

## What Was Left Incomplete

- Scoring redesign (Phase 1 — GREEN/YELLOW/RED) — still not started, only scoped
- Voice/TTS feature — scoped, not implemented
- Analytics export (CSV) — scoped, not implemented
- Accuracy data collection — needs multiple real shifts before we can evaluate
- Zone text extraction from idle screen — discussed but not implemented (not
  needed for current algorithm, zone only would help disambiguate multiple
  same-name restaurants, deferred until accuracy data shows it's needed)

---

## Known Broken (do not ignore)

- Play Store install forces reinstall (not update) — app is unreviewed/unpublished
  → Data wipe on install until Google review is completed
- Chart shows ALL captured orders across sessions not just completed ones
- Map debug logging (map_debug.log) confirmed DoorDash exposes NO useful location
  data — this investigation is CLOSED, do not re-investigate map content descriptions
- screen_texts.log confirms accessibility tree text between "pickup"/restaurant
  name and "customer dropoff" contains NOTHING — no address, no street name reliably

---

## Next Session — Start Here

1. Confirm v154 installed and working:
   - Pill shows net profit + delivery town
   - Confirmation notification appears after offer detected
   - Yes/No taps save to DB correctly
2. After 5-10 shifts with confirmations logged, run accuracy query:
```sql
   SELECT COUNT(*) as total,
     SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) as correct,
     ROUND(100.0 * SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) / COUNT(*), 1) as accuracy_pct
   FROM offer_captures
   WHERE estimatedTown IS NOT NULL AND townAccurate IS NOT NULL;
```
3. Decision point: if accuracy >80% → GPS geocoding approach is sufficient, drop
   MediaProjection entirely. If <80% → reconsider paid-tier map screenshot approach.
4. Start Phase 1 scoring redesign (GREEN/YELLOW/RED) — ~4.5 hours, see
   FEATURE_BACKLOG.md for full scope

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated on real shift |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | pay, distance, restaurant, countdown |
| Offer dedup guard (receiver-level) | ✅ Fixed | v154 — survives service restarts |
| Delivery town estimation | ✅ Built | GPS + Nominatim geocode + bearing projection |
| Town accuracy tracking | ✅ Built | Yes/No notification, DB columns, awaiting real data |
| Pill shows town | ✅ Added | PILL/MINI/FULL all updated |
| MediaProjection dialog suppression | ✅ Fixed | No dialog for accessibility/tap modes |
| Tap-to-capture mode | ✅ Added | Saved as "tap" in Settings |
| LIVE badge in accessibility mode | ✅ Fixed | |
| Accessibility state in Settings | ✅ Fixed | |
| Debug APK signing | ✅ Fixed | Uses release keystore |
| deploy.sh port persistence | ✅ Added | .pixel_port file, prompts only on failure |
| Scoring redesign (Phase 1) | 🔲 Scoped | GREEN/YELLOW/RED profit verdict — not started |
| Voice/TTS | 🔲 Scoped | Not started |
| Analytics export | 🔲 Scoped | Not started |
| Order archive (day/week/month) | 🔲 Not started | |
| ScreenCaptureService removal | 🔲 Planned | Dedicated 2-3 hour session |
| Play Store review submission | 🔲 Pending | |

---

## Decisions Made This Session

1. MediaProjection map screenshot approach explicitly rejected for now — poor UX,
   driver preference is GPS-based estimation. Will revisit ONLY if accuracy data
   shows GPS approach is insufficient (<80% threshold).
2. Delivery town estimation uses Option 1 (Nominatim nearest POI to driver GPS) as
   default; Option 2 (city-based search) as fallback/driver-selectable in future.
3. Zone text from idle screen ("nj: moorestown/mt. laurel") is the driver's
   ASSIGNED zone, not delivery zone — confirmed NOT to use for delivery estimation,
   only potentially useful as restaurant geocode search anchor if needed later.
4. Accuracy tracking built BEFORE scoring redesign — need real data to decide
   if/how town confidence should factor into scoring.
5. Receiver-level dedup guard added as defense-in-depth alongside service-level
   guard — protects against service restarts resetting in-memory state.

---

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: IP 10.0.0.110, port saved in ~/giglens/.pixel_port (currently 36103,
  changes on reboot — deploy.sh handles reconnection automatically)
- Samsung S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Keystore: ~/giglens/giglens-release.keystore
- Play Store JSON key: ~/giglens/google-play-api-key.json (DO NOT COMMIT)
- MOBSF_APIKEY now in ~/.bashrc as env var, not hardcoded in pre-commit hook

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/service/AccessibilityOfferReceiver.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/TownAccuracyReceiver.kt (NEW)
- app/src/main/java/com/augusteenterprise/giglens/geocoding/DeliveryTownEstimator.kt (NEW)
- app/src/main/java/com/augusteenterprise/giglens/data/OfferCapture.kt
- app/src/main/java/com/augusteenterprise/giglens/data/OfferCaptureDao.kt
- app/src/main/java/com/augusteenterprise/giglens/data/OfferDatabase.kt (migration 7→8)
- app/src/main/AndroidManifest.xml
- deploy.sh
- .git/hooks/pre-commit (MOBSF_APIKEY env var)

---

## Features Left to Implement (Priority Ranked)

See docs/FEATURE_BACKLOG.md for full list and tiered monetization model.

### High Priority
1. **Collect town accuracy data** — need 5-10 shifts of Yes/No confirmations before deciding architecture
2. **Scoring redesign (Phase 1)** — GREEN/YELLOW/RED profit verdict, ~4.5 hours
3. **ScreenCaptureService removal** — dedicated 2-3 hour session

### Medium Priority
4. Order archive view, voice/TTS, analytics export — see FEATURE_BACKLOG.md

---

*Next developer: read SESSION_PROTOCOL.md first, then return here.*
