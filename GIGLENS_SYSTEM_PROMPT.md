# GigLens — System Prompt & Developer Context

## PROJECT OVERVIEW

**GigLens** is an Android app for gig economy drivers (DoorDash, Uber Eats, Grubhub, Instacart, Amazon Flex) that automatically captures, logs, and analyzes delivery offer data. Drivers share screenshots of offers to GigLens, which uses on-device OCR to extract pay, distance, and restaurant information — building a personal offer history with analytics.

**Owner:** Milo Auguste / Auguste Enterprise  
**Repository:** `miloauguste/giglens`  
**Current Version:** 0.1.1  
**Status:** MVP — share-to-capture working with DoorDash OCR  
**License:** Proprietary  

---

## ARCHITECTURE

### Tech Stack
- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Build:** Gradle 8.7, AGP 8.2.2, Kotlin 1.9.22
- **OCR:** Google ML Kit Text Recognition (on-device, no cloud)
- **Database:** Room (SQLite abstraction)
- **UI:** Material Components, ViewBinding
- **Annotation Processing:** kapt (required for Room)

### Development Environment
- **Build server:** `milo-dev` (headless Linux, i9 CPU, 48GB RAM, GTX 3050)
- **Code editor:** VS Code Remote-SSH from laptop to `milo-dev`
- **Test device:** Samsung Galaxy S20 connected via USB/ADB
- **No emulator** — all testing on physical device
- **Project path:** `/home/poppa/giglens/`

### Package Structure
```
com.augusteenterprise.giglens/
├── GigLensApp.kt              # Application class, Room DB init, notification channel
├── data/
│   ├── OfferCapture.kt         # Room entity — single captured offer
│   ├── OfferCaptureDao.kt      # Room DAO — CRUD + analytics queries
│   └── OfferDatabase.kt        # Room database singleton
├── ocr/
│   └── OfferParser.kt          # OCR text → structured offer data
├── service/
│   ├── OfferDetectorService.kt # Accessibility Service (auto mode, opt-in)
│   ├── OfferNotificationService.kt # NotificationListener (optional)
│   └── ScreenCaptureService.kt # MediaProjection screenshot + OCR (auto mode)
└── ui/
    ├── MainActivity.kt         # Home screen — stats, mode toggle
    └── ShareReceiverActivity.kt # Receives shared screenshots, runs OCR
```

---

## CAPTURE MODES (Priority Order)

### 1. Share-to-Capture (DEFAULT — zero permissions)
- Driver screenshots an offer → Share → GigLens
- `ShareReceiverActivity` receives image via `ACTION_SEND`
- Runs ML Kit OCR → `OfferParser` extracts data → saves to Room DB
- Shows toast with extracted offer summary
- **No special permissions required**

### 2. Auto Mode (OPT-IN — Accessibility Service)
- `OfferDetectorService` monitors DoorDash for Accept/Decline/$ signals
- When detected, signals `ScreenCaptureService` to capture screen
- Screenshot → ML Kit OCR → parse → save
- **Requires Accessibility Service permission** (scary prompt)
- Presented as "Enhanced Mode" behind an explanatory dialog
- Only offered after user is comfortable with the app

### 3. Notification Listener (OPTIONAL)
- `OfferNotificationService` reads DoorDash notifications
- Extracts offer data from notification text
- **Requires Notification Access permission** (moderate prompt)
- Secondary option — notification format varies by platform/version

---

## OCR PARSER RULES

### Keyword Detection (`isOfferScreen`)
Requires **2+ keywords** AND a **dollar amount** to classify as an offer screen.

**Keywords:** accept, decline, delivery for, deliver by, total may be higher, includes doordash pay, guaranteed, pickup, pick up, customer dropoff

### Pay Extraction
- Primary regex: `\$\s?(\d{1,3}(?:\.\d{2})?)` — matches `$7.50`
- OCR fallback regex: `(?<![A-Za-z])S(\d{1,3}\.\d{2})` — matches `S4.70` (OCR misreads $ as S)
- OCR fallback requires decimal point to avoid false positives (e.g., `PYS66`)
- Takes the **maximum** dollar value found (offer amount is typically the largest)
- Filters to range $1.00–$999.00

### Distance Extraction
- Regex: `(\d{1,3}(?:\.\d{1,2})?)\s*(?:mi(?:les?)?)` — matches `2.7 mi`, `5 miles`

### Restaurant Extraction
- **Strategy 1:** Scan lines after "Pickup" keyword, skip garbage (min 3 chars, must start with uppercase, must contain letters)
- **Strategy 2:** Find capitalized lines that aren't UI elements or map text
- **Skip words:** accept, decline, total, guaranteed, delivery, deliver, doordash, instructions, items, customer, dropoff, pickup, mapbox, center, rd, ave, st, blvd, turnpike

### Platform Detection
Based on OCR text content:
- "doordash" / "dasher" → DoorDash
- "uber eats" / "uber" → Uber Eats
- "grubhub" → Grubhub
- "instacart" → Instacart
- "amazon flex" → Amazon Flex
- Default: "Unknown"

---

## DATABASE SCHEMA

### Table: `offer_captures`
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PRIMARY KEY | Auto-increment |
| timestamp | LONG | Capture time (epoch ms) |
| platform | TEXT | DoorDash, Uber Eats, etc. |
| payAmount | DOUBLE? | Extracted dollar amount |
| distance | DOUBLE? | Extracted mileage |
| distanceUnit | TEXT | "mi" or "km" |
| restaurant | TEXT? | Extracted restaurant name |
| screenshotPath | TEXT? | Path to saved screenshot |
| rawOcrText | TEXT? | Full OCR output for debugging |
| accepted | BOOLEAN? | null=unknown, true/false if detected |

### Analytics Queries (in DAO)
- `getCount()` — total offers captured
- `getAveragePay()` — mean pay across all offers
- `getAveragePayPerMile()` — mean pay/distance ratio
- `getRecent(limit)` — last N offers
- `getByDateRange(start, end)` — offers within date window

---

## SECURE DEVELOPMENT PRACTICES

### Data Privacy
1. **All processing is on-device.** ML Kit OCR runs locally — no images or text are sent to any server.
2. **Screenshots are stored in app-private storage** (`getExternalFilesDir()`) — not accessible to other apps.
3. **No user accounts, no cloud sync, no analytics SDK** in MVP. If added later, require explicit opt-in.
4. **No PII collection.** The app captures offer data (pay, distance, restaurant), not driver identity, location history, or earnings.
5. **Rejected screenshots are deleted immediately** — if OCR determines the image is not an offer, the saved copy is removed.
6. **rawOcrText is stored for debugging only** — strip or encrypt before any future cloud sync.

### Permissions Principle of Least Privilege
7. **Default mode requires ZERO permissions.** Share-to-capture uses standard Android share intent.
8. **Accessibility Service is opt-in only** — never requested on first launch. Requires explicit user action through settings.
9. **Notification Listener is opt-in only** — same principle.
10. **POST_NOTIFICATIONS** — requested for foreground service notification (auto mode only).

### Code Security
11. **No hardcoded secrets.** No API keys, tokens, or credentials in source code. If keys are needed later, use `local.properties` (gitignored) or Android Keystore.
12. **No network calls in MVP.** The app is fully offline. When networking is added:
    - Use HTTPS only (certificate pinning for sensitive endpoints)
    - Validate all server responses
    - Implement proper error handling for network failures
13. **Input validation on all OCR output.** Pay amounts filtered to $1–$999. Distance filtered to reasonable range. Restaurant names capped at 50 chars.
14. **No dynamic code loading.** No reflection, no scripting engines, no WebView with JS execution.
15. **ProGuard/R8 enabled for release builds.** Obfuscate code and strip debug info.

### Dependency Security
16. **Minimal dependencies.** Only use well-maintained, widely-adopted libraries:
    - AndroidX (Google)
    - ML Kit (Google)
    - Room (Google)
    - Material Components (Google)
    - Kotlin Coroutines (JetBrains)
17. **Pin dependency versions.** No `+` or `latest` in version strings.
18. **Review dependency updates before applying.** Check changelogs and security advisories.
19. **No third-party analytics, crash reporting, or ad SDKs** without explicit product decision and privacy review.

### Build & Release Security
20. **Debug and release builds are separate.** Debug builds have logging enabled; release builds strip all `Log.d/Log.i` calls.
21. **Sign release APKs with a dedicated keystore.** Store keystore file outside the repo. Never commit keystore or passwords.
22. **Version bumping is automated** via pre-commit hook — prevents manual version conflicts.
23. **Git history is clean.** No secrets, credentials, or sensitive data in any commit.

### Google Play Compliance
24. **Accessibility Service usage must be justified** in Play Store listing. Document exactly what the service does and why.
25. **Privacy policy required** before Play Store submission. Must disclose: data collected, storage method, no cloud transmission.
26. **Target API level must stay current.** Google requires targeting within 1 year of latest SDK.

### Future Security Considerations (when adding networking)
27. **Authentication:** OAuth 2.0 or token-based auth. Never store passwords locally.
28. **Data in transit:** TLS 1.3, certificate pinning.
29. **Data at rest:** Encrypt Room database with SQLCipher if storing sensitive data.
30. **API rate limiting:** Prevent abuse if fleet dashboard API is exposed.
31. **GDPR/CCPA compliance:** Data export, deletion requests, retention policies.

---

## STANDING DEVELOPMENT RULES

### Build & Deploy
1. **All builds on `milo-dev`** — headless Linux server, command-line only.
2. **Test on physical device (S20 via ADB)** — no emulator.
3. **Build command:** `cd /home/poppa/giglens && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **Always check `adb logcat` after install** for crash logs before declaring success.
5. **Pre-commit hook auto-bumps version** — never manually edit `versionName` or `versionCode`.

### Code Quality
6. **Every file must have an Author comment** identifying which LLM wrote/modified it.
7. **Every Kotlin file must compile** — run `./gradlew assembleDebug` before committing.
8. **Log all significant operations** — use `Log.d(TAG, ...)` for debugging, `Log.i(TAG, ...)` for milestone events, `Log.e(TAG, ...)` for errors.
9. **No bare `catch` blocks** — always log the exception.
10. **Use coroutines for all database and I/O operations** — never block the main thread.

### Git Workflow
11. **Commit messages must be descriptive** — what changed and why.
12. **Push to `origin main` after each working session.**
13. **Tag releases:** `git tag -a v0.x.x -m "description"` for milestone versions.

### Testing Protocol
14. **Share-to-capture test:** Share a known DoorDash screenshot → verify pay, distance, restaurant in logcat.
15. **Negative test:** Share a non-offer image → verify "Couldn't detect an offer" response.
16. **Stats test:** Open app → verify capture count and averages update correctly.
17. **Crash test:** `adb logcat -d | grep "FATAL EXCEPTION"` after every install.

---

## FEATURE ROADMAP

### Phase 1 — MVP (CURRENT) ✅
- [x] Share-to-capture (zero permissions)
- [x] ML Kit OCR extraction (pay, distance, restaurant)
- [x] Room database storage
- [x] Main screen with capture count and avg stats
- [x] Auto mode opt-in (Accessibility Service)
- [x] Version auto-bumping

### Phase 2 — Beta-Ready
- [ ] Floating status widget
- [ ] Offer history view (RecyclerView with date filters)
- [ ] Multi-platform OCR (Uber Eats, Grubhub, Instacart, Amazon Flex)
- [ ] UI design pass (dark theme, custom icon, onboarding)
- [ ] Pay-per-mile color coding (green/yellow/red)
- [ ] Daily/weekly earnings summary

### Phase 3 — Analytics
- [ ] Best hours heatmap
- [ ] Restaurant ranking
- [ ] Goal tracker ("Make $150 today" progress bar)
- [ ] End-of-day push notification recap
- [ ] Export to CSV (tax records)
- [ ] Decline reason logging

### Phase 4 — Growth
- [ ] Cross-platform offer comparison
- [ ] Smart alerts ("This offer is 40% above your average")
- [ ] Community stats (anonymized, requires backend)
- [ ] Multi-app stacking insights

### Phase 5 — Business
- [ ] Fleet operator web dashboard
- [ ] White-label mode
- [ ] API for fleet management integration
- [ ] Freemium model: free (capture + 30-day history) / Pro $2.99/mo (full analytics)
- [ ] 7-day free trial flow

---

## GO-TO-MARKET

### Beta Launch
- **Channel:** Facebook groups (DoorDash Drivers, Uber Eats Driver Community, Gig Workers United)
- **Offer:** Free 7-day trial, no credit card required
- **Target:** Major metro areas (NYC, Philly, Atlanta, Chicago)
- **Beta group:** Premier Driver Rentals fleet drivers (when available)

### Pricing (Post-Beta)
- **Free tier:** Capture + log, last 30 days, no analytics
- **Pro tier:** $2.99/month or $24.99/year — full analytics, unlimited history, multi-platform

### Competitive Advantage
- Zero-permission default (share-to-capture) — no scary prompts
- On-device processing — no data leaves the phone
- Multi-platform from day one (Phase 2)
- Built by a fleet operator who understands the driver's perspective

---

## KNOWN OCR CHALLENGES

| Issue | Cause | Mitigation |
|-------|-------|------------|
| `$` read as `S` | ML Kit font confusion on dark backgrounds | Dual regex: real `$` + OCR `S` with decimal required |
| Map text in OCR output | Street names, coordinates, UUID fragments | Skip words list, length/case filters |
| Restaurant on wrong line | OCR line order varies by scan | Scan 3 lines after "Pickup", apply quality filters |
| Inconsistent OCR between scans | Same image can produce different text | Keyword threshold is 2 (not exact match) |
| Uber Eats/Grubhub formats unknown | Not yet tested | Phase 2: collect sample screenshots, build per-platform rules |

---

## QUICK REFERENCE COMMANDS

```bash
# Build and install
cd /home/poppa/giglens && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logs (GigLens only)
adb logcat -s ShareReceiver:* OfferParser:* OfferDetector:* ScreenCapture:* GigLensApp:*

# Check for crashes
adb logcat -d | grep "FATAL EXCEPTION" | tail -5

# Clear logs before testing
adb logcat -c

# Check device connection
adb devices

# Clean build
cd /home/poppa/giglens && ./gradlew clean assembleDebug

# Git commit and push
cd /home/poppa/giglens && git add -A && git commit -m "description" && git push

# Check current version
cat /home/poppa/giglens/version.txt
```
