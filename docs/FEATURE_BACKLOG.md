# GigLens — Feature Backlog
**Last updated:** 2026-06-15 (v154)

---

## Phase 1 — Next Dedicated Session (~4.5 hours)

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 1 | **Scoring redesign** | GREEN/YELLOW/RED profit verdict, configurable thresholds in Settings, no hardcoded ceiling | 1.5hr | Free |
| 2 | **Pill header polish** | Refine net profit + town layout once accuracy data confirms approach | 30min | Free |
| 3 | **Voice/hands-free** | Android TTS reads offer aloud on pill appear — profit, distance, color, town | 1.5hr | Free/Paid |
| 4 | **Analytics export** | CSV export of offer/shift history via Android share sheet | 1hr | Free/Paid |

---

## ✅ Completed This Session (v154)

| Feature | Status | Notes |
|---------|--------|-------|
| Delivery town estimation | ✅ Built | GPS + Nominatim geocode + bearing projection — see DeliveryTownEstimator.kt |
| Town accuracy tracking | ✅ Built | Yes/No notification after offer, DB columns added (migration 7→8) |
| Pill shows town | ✅ Added | "$8.42 📍 ~Cherry Hill" — PILL/MINI/FULL states |
| Receiver-level dedup guard | ✅ Fixed | Survives OfferDetectorService restarts |
| deploy.sh port persistence | ✅ Added | .pixel_port file, only prompts on connection failure |

---

## 🔬 Decision Point — Delivery Town Architecture (BLOCKING)

**Before building anything else town-related, we need real accuracy data.**

Run this after 5-10 shifts with Yes/No confirmations logged:
```sql
SELECT COUNT(*) as total,
  SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) as correct,
  ROUND(100.0 * SUM(CASE WHEN townAccurate = 1 THEN 1 ELSE 0 END) / COUNT(*), 1) as accuracy_pct
FROM offer_captures
WHERE estimatedTown IS NOT NULL AND townAccurate IS NOT NULL;
```

- **>80% accurate** → GPS geocoding approach confirmed sufficient. Drop MediaProjection
  permanently. Proceed with Phase 1/2 features as planned.
- **<80% accurate** → Reconsider paid-tier map screenshot + pixel pin detection
  (requires MediaProjection, explicitly avoided so far due to poor UX — only
  revisit if data demands it).

**Confirmed dead ends (do not re-investigate):**
- DoorDash accessibility tree exposes NO coordinates, addresses, or map tile URLs
- Content descriptions on map view are empty
- Text between restaurant name and "customer dropoff" label contains nothing useful
- Idle-screen zone text ("nj: moorestown/mt. laurel") is driver's ASSIGNED zone,
  not delivery zone — not useful for delivery town estimation

---

## Phase 2 — Paid Features

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 5 | **Town estimation upgrade (conditional)** | Only if accuracy <80% — map screenshot + pin detection via MediaProjection | 4-6hr | Tier 2/3 |
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
| High-confidence town (map-based, if needed) | ❌ | ✅ | ✅ |
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
