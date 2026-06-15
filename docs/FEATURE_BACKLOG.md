# GigLens — Feature Backlog
**Last updated:** 2026-06-15
**Version:** 0.1.145

---

## Phase 1 — Next Dedicated Session (~4.5 hours)

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 1 | **Scoring redesign** | GREEN/YELLOW/RED profit verdict, configurable thresholds in Settings, no hardcoded ceiling | 1.5hr | Free |
| 2 | **Pill header redesign** | Show net profit + delivery town placeholder (📍 ---) | 30min | Free |
| 3 | **Voice/hands-free** | Android TTS reads offer aloud on pill appear — profit, distance, color, town | 1.5hr | Free/Paid |
| 4 | **Analytics export** | CSV export of offer/shift history via Android share sheet | 1hr | Free/Paid |

---

## Phase 2 — Paid Features

| # | Feature | Description | Effort | Tier |
|---|---------|-------------|--------|------|
| 5 | **Delivery town estimation** | Reverse geocode restaurant + distance → estimated drop-off town. First validate via map_debug.log — accessibility tree may expose coordinates directly | 3-4hr | Tier 2/3 |
| 6 | **FB sentiment batch** | Nightly scraper + Ollama sentiment → restaurant reputation DB. Tier 2: hosted cloud (Supabase/Railway). Tier 3: self-hosted on driver's desktop via Telegram bot | 6-8hr | Tier 2/3 |
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

---

## Tiered Monetization Model

| Feature | Tier 1 Free | Tier 2 Standard | Tier 3 Self-Hosted |
|---------|:-----------:|:---------------:|:-----------------:|
| Color pill + net profit | ✅ | ✅ | ✅ |
| Voice readout (profit + distance + color) | ✅ | ✅ | ✅ |
| CSV export (current shift) | ✅ | ✅ | ✅ |
| Delivery town in pill | ❌ | ✅ | ✅ |
| Voice reads town name | ❌ | ✅ | ✅ |
| Restaurant sentiment score | ❌ | ✅ | ✅ |
| PDF shift report + date range | ❌ | ✅ | ✅ |
| Google Sheets sync | ❌ | ✅ | ✅ |
| Self-hosted DB via Telegram bot | ❌ | ❌ | ✅ |

---

## Tabled / Low Priority

| # | Feature | Reason tabled |
|---|---------|---------------|
| T1 | Robolectric/Espresso automated testing | Too early — app still in active development |
| T2 | Android 14 partial screen sharing | Irrelevant after ScreenCaptureService removal |
| T3 | ConnectionRecord leaks | ~25 dead entries, low impact, no user-facing effect |

---

## Implementation Notes

### Delivery Town — Investigation First
Before building estimation logic, pull map_debug.log after next shift:
```bash
adb -s 10.0.0.110:<PORT> pull \
  /sdcard/Android/data/com.augusteenterprise.giglens/files/debug/map_debug.log \
  ~/giglens/docs/map_debug.log
```
If accessibility tree exposes coordinates → simple reverse geocode (Nominatim, free).
If not → fallback to restaurant geocode + distance radius estimation.

### Voice Feature — Spoken Format
"$8.42 net. 6.2 miles. Cherry Hill. Green offer."

"$4.10 net. 8.5 miles. Destination unknown. Yellow offer."

### Phase 2 Architecture (Production)
Nightly batch (milo-dev or cloud cron)

FB scraper → Ollama sentiment → pushes to cloud DB (Supabase)
Phone at offer time

restaurant name → HTTPS API → cloud DB → sentiment score returned
Tier 3 self-hosted

restaurant name → Telegram bot → driver's local DB → score returned

---

*Update this file at the start of every session. Move completed items to LAST_SESSION.md.*
