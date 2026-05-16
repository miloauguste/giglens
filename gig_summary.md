# GigLens — Session Summary (May 15, 2026)

---

## CURRENT STATE

**Version:** 0.1.7
**Status:** MVP + scoring engine + settings screen
**Device:** Samsung Galaxy S20 (R3CN509D8WX) via ADB
**Build server:** milo-dev (headless Linux, i9, 48GB RAM)
**Repo:** miloauguste/giglens (main branch)

---

## WHAT WAS DONE THIS SESSION

### Security
1. OWASP dependency scan passing — upgraded plugin 9.0.9 to 10.0.4, disabled OSS Index, created suppression file for false positives. NVD API key stored in local.properties.

### Scoring Engine
2. Composite offer scorer (OfferScorer.kt) — DB-driven weights, reads all thresholds from scorer_config table. No hardcoded values.
3. Net value scoring — primary metric is offer_pay - (total_miles x cost_per_mile). Drivers see real profit after vehicle costs before accepting.
4. 4-factor formula: net value (50%), pickup penalty (30%), true $/mile (20%).
5. Vehicle cost per mile — default $0.90/mi (IRS standard), stored in scorer_config, adjustable in Settings.

### Distance Estimation
6. StreetExtractor (StreetExtractor.kt) — extracts pickup and dropoff street names from OCR text. Scans 10 lines before AND after keywords.
7. GeocodingHelper (GeocodingHelper.kt) — Nominatim free geocoding, no API key. Road distance = straight-line x 1.3. Pro tier hook: Google Maps Distance Matrix API.
8. Reverse geocoding — GPS lat/lon to region string, auto-saved to app_config for geocoding hint.

### Location
9. LocationHelper (LocationHelper.kt) — GPS fix at capture time, 5-second timeout, falls back to last known location.
10. ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION added to manifest.

### Database
11. Migration 1 to 2 — score columns on offer_captures, scorer_config table.
12. Migration 2 to 3 — pickup/delivery/total distance, vehicle cost, net value columns.
13. Migration 3 to 4 — app_config string settings table.
14. AppConfig entity — key-value store: driver_region, driver_manual_region, driver_state, gps_enabled, maps_api_key, data_sharing.

### Settings Screen
15. SettingsActivity — GPS toggle with permission request, auto-detects region via reverse geocode, manual region fallback, cost per mile (saved to scorer_config), data sharing preference.

### Standing Rules Added
16. No hardcoded values rule — added to CLAUDE.md. All configurable values must live in scorer_config or app_config. No string literals in business logic.
17. Python edits rule — all file edits use Python replace pattern, not sed or nano.

---

## CURRENT DB SCHEMA

### offer_captures (v3)
id, timestamp, platform, payAmount, distance, distanceUnit, restaurant,
screenshotPath, rawOcrText, accepted, score, verdict, payPerMile,
vsPersonalAvg, driverLat, driverLon, pickupDistance, deliveryDistance,
totalDistance, truePayPerMile, vehicleCost, netValue, estimatedMinutes

### scorer_config
key, value, description
Keys: weight_pay_per_mile, weight_total_pay, weight_distance,
weight_pickup_penalty, weight_true_pay_per_mile, weight_total_pay_v2,
weight_delivery_leg, pay_per_mile_min/max, total_pay_min/max,
distance_min/max, pickup_distance_min/max, true_pay_per_mile_min/max,
floor_pay_per_mile, floor_total_pay, threshold_take, threshold_borderline,
cost_per_mile

### app_config
key, value, description
Keys: driver_region, driver_manual_region, driver_state, gps_enabled,
maps_api_key, data_sharing

---

## KNOWN ISSUES

1. Pickup street extraction — Westfield Rd not extracted, appears too far before Pickup keyword. Needs wider scan or alternative strategy.
2. OCR typos in street names — Briags Rd (misread of Briggs Rd) fails geocoding. Region hint helps but does not fully solve.
3. Restaurant extraction — still grabbing Waltc instead of Panera Bread. Pre-existing OfferParser bug.
4. Location permission not yet requested at capture time — driver must enable GPS in Settings first.
5. Geocoding partial — pickup street often null, scorer uses 0.75 neutral assumption.

---

## WHAT IS NEXT

### Immediate
1. Fix restaurant extraction bug in OfferParser
2. Fix pickup street extraction
3. Add runtime location permission request in ShareReceiverActivity
4. Grant location permission on device and test full geocoding flow
5. Update CLAUDE.md with session rules
6. Result display overhaul — simplified one-liner output (e.g. DD offer $8.50, approx gross: $7.25, est. net: $6.72)
7. Position-aware display — result widget appears at top/middle/bottom based on where offer sits on screen
8. Display type choice — Toast (auto-dismiss) vs overlay card (stays until dismissed) vs status bar notification

### Phase 2 (Beta-Ready)
6. Offer history view (RecyclerView with date filters)
7. UI design pass (dark theme, custom icon, onboarding)
8. Floating status widget
9. Pay-per-mile color coding
10. Daily/weekly earnings summary

### Phase 3 (Analytics)
11. Best hours heatmap
12. Restaurant ranking
13. Goal tracker
14. End-of-day notification recap
15. CSV export

### Security (Before Beta)
16. SQLCipher Room encryption
17. ProGuard/R8 for release builds
18. Strip debug logging from release
19. Input validation hardening
20. Privacy policy
21. Accessibility Service justification for Play Store

---

## QUICK START FOR NEXT SESSION

    # Verify device
    adb devices

    # Build and install
    cd /home/poppa/giglens && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

    # Watch logs while testing
    adb logcat -T 1 | grep -E "ShareReceiver|StreetExtractor|GeocodingHelper|OfferParser|SettingsActivity"

    # Check for crashes
    adb logcat -d | grep -A5 "FATAL EXCEPTION" | head -30

    # OWASP scan
    NVD_KEY=$(grep "owasp.nvd.api.key" /home/poppa/giglens/local.properties | cut -d= -f2)
    ./gradlew :app:dependencyCheckAnalyze -Powasp.nvd.api.key="$NVD_KEY"

    # Git status
    cd /home/poppa/giglens && git status && cat version.txt

---

## FILE INVENTORY

    /home/poppa/giglens/
    CLAUDE.md                          # System prompt + standing rules
    gig_summary.md                     # This file
    version.txt                        # 0.1.7
    local.properties                   # NVD API key (gitignored)
    dependency-check-suppressions.xml  # OWASP false positive suppressions
    app/src/main/java/com/augusteenterprise/giglens/
        GigLensApp.kt
        data/
            AppConfig.kt               # String settings entity + keys + defaults
            AppConfigDao.kt
            OfferCapture.kt            # v3 schema with score + distance fields
            OfferCaptureDao.kt         # includes getAverageScore()
            OfferDatabase.kt           # v4, migrations 1-2-3-4
            ScorerConfig.kt            # Numeric settings entity + keys + defaults
            ScorerConfigDao.kt
        geocoding/
            GeocodingHelper.kt         # Nominatim geocoding + reverse geocode
        location/
            LocationHelper.kt          # GPS fix with timeout
        ocr/
            OfferParser.kt             # Pay/distance/restaurant extraction
            StreetExtractor.kt         # Pickup/dropoff street extraction
        scoring/
            OfferScorer.kt             # 4-factor net value scorer
        service/
            OfferDetectorService.kt
            OfferNotificationService.kt
            ScreenCaptureService.kt
        ui/
            MainActivity.kt
            SettingsActivity.kt        # GPS, region, cost, data sharing
            ShareReceiverActivity.kt   # Full capture + score + geocode pipeline
    app/src/main/res/layout/
        activity_main.xml
        activity_settings.xml

---

## SECURITY & FEATURE ROADMAP (Priority Order)

### SDLC Security Policy (always enforced)
- Security layer must be implemented AS features are added, never after
- Every new feature must include its security requirements before coding begins
- Continuous scanning is part of every session — run all scans before ending
- No feature is complete without its corresponding security layer passing all scans

### Security Scan Suite (run every session end)
1. OWASP dependency check — ./gradlew :app:dependencyCheckAnalyze
2. Android Lint — ./gradlew :app:lint
3. Gitleaks — scan git history for secrets (TO INSTALL)
4. Semgrep — Kotlin static analysis for insecure patterns (TO INSTALL)
5. MobSF — full APK analysis via Docker (TO INSTALL)

### Encryption Requirements (by priority)
CRITICAL — must be done before beta:
1. Move screenshots from external storage to app-private storage (getFilesDir)
   - Current: getExternalFilesDir() — accessible to other apps
   - Fix: getFilesDir() — app-private, no other app access
   - Security layer for: share-to-capture feature

2. SQLCipher Room database encryption
   - Current: plain SQLite readable on rooted devices
   - Fix: SQLCipher with key from Android Keystore (hardware-backed)
   - Security layer for: all DB features (scoring, offers, config)
   - Dependencies: net.zetetic:android-database-sqlcipher:4.5.4

3. EncryptedSharedPreferences for tokens
   - Current: not implemented (no auth yet)
   - Fix: AndroidX Security library backed by Android Keystore
   - Security layer for: Feature — user authentication
   - Dependency: androidx.security:security-crypto:1.1.0-alpha06

HIGH — must be done before networking:
4. Network Security Config
   - HTTPS only, no cleartext traffic
   - Certificate pinning for API endpoints
   - Security layer for: backend sync, community data features

5. TLS 1.3 + certificate pinning
   - Security layer for: community benchmarking, fleet dashboard

MEDIUM — before Play Store:
6. ProGuard/R8 minification and obfuscation
   - Current: isMinifyEnabled = false
   - Security layer for: release builds

7. Strip debug logging from release
   - Current: Log.d/Log.i calls expose OCR text, GPS, pay amounts
   - Fix: ProGuard rule to strip all debug logs in release

8. Input validation hardening
   - Sanitize all OCR output before DB insertion
   - Validate screenshot file size
   - Rate-limit share processing

---

## FEATURE BACKLOG (categorized and prioritized)

### P0 — Fix Now (blocking quality)
1. Restaurant extraction bug — grabbing map garbage instead of restaurant name
2. Pickup street extraction — too narrow scan window
3. Runtime location permission request at capture time
4. Result display overhaul — simplified one-liner toast
5. Position-aware display widget (top/middle/bottom)

### P1 — Beta Required
6. Offer history view (RecyclerView + date filters)
   Security: read-only DAO queries, no raw SQL
7. UI design pass (dark theme, icon, onboarding)
8. Floating status widget
   Security: SYSTEM_ALERT_WINDOW permission, opt-in only
9. Pay-per-mile color coding (green/yellow/red)
10. Daily/weekly earnings summary
11. Move screenshots to app-private storage (SECURITY)
12. SQLCipher DB encryption (SECURITY)

### P2 — Post-Beta
13. User authentication (email + password)
    Security: bcrypt server-side, JWT with 15min expiry,
    EncryptedSharedPreferences, refresh token rotation
14. Multi-platform OCR (Uber Eats, Grubhub, Instacart, Amazon Flex)
15. CSV export for tax records
    Security: no PII in export, driver controls data
16. Decline reason logging
17. Goal tracker (Make 50 today progress bar)
18. End-of-day push notification recap
19. ProGuard/R8 release build (SECURITY)
20. Strip debug logs from release (SECURITY)

### P3 — Growth Features
21. Best hours heatmap
22. Restaurant ranking
23. Cross-platform offer comparison
24. Smart alerts (This offer is 40% above your average)
25. Community stats — anonymized aggregates
    Security: differential privacy, no individual identification,
    explicit opt-in, GDPR/CCPA compliant data deletion
26. Google Maps Distance Matrix API (Pro tier)
    Security: API key in app_config DB, never in source code

### P4 — Business/Monetization
27. Fleet operator web dashboard
    Security: role-based access control, audit logs,
    encrypted data in transit and at rest
28. White-label mode
29. Freemium model (Free 30-day / Pro .99/mo)
    Security: receipt validation via Play Billing,
    server-side entitlement check
30. API for fleet management integration
    Security: OAuth 2.0, rate limiting, API key rotation

### P5 — Platform
31. Privacy policy (required before Play Store)
32. Accessibility Service justification for Play Store
33. GDPR/CCPA compliance — data export, deletion, retention policy
34. Play Store submission

---

## APPROVED UI DESIGN — GigLens Overlay Widget (May 16, 2026)

### Layout approved by Milo Auguste

**Framework:** Jetpack Compose + WindowManager overlay
**Permission required:** SYSTEM_ALERT_WINDOW (opt-in, same as chat heads)

### Three interaction states:
1. Score pill — floating, minimal, shows verdict color + score number only
2. Tap → mini drawer — slides open from pill, one-liner summary (offer, net, distance)
3. Pull/expand → full detail — complete breakdown, vehicle cost, vs avg, pickup estimate

### Design rules (never change):
- Widget is draggable anywhere on screen by the driver
- Position saved to app_config DB — remembered between sessions
- Accept button (DoorDash) must NEVER be obscured at any state
- Drawer opens FROM the widget position — top widget = opens downward, bottom = opens upward, left = opens right, right = opens left
- Dark theme only — matches DoorDash dark UI
- Pill size: minimal — score number + verdict dot only when collapsed
- Full detail card width: 200dp max — does not dominate the screen

### Compose implementation plan:
- WindowManager + TYPE_APPLICATION_OVERLAY for floating widget
- MotionLayout or AnimatedVisibility for pill → mini → full transitions  
- SheetState enum: PILL | MINI | FULL
- Draggable modifier with boundary constraints (screen edges)
- Position persistence: appConfigDao.setValue(WIDGET_X, WIDGET_Y) on drag end

### Reference rendering: giglens_draggable_widget_demo

---

## APPROVED UI DESIGN — Onboarding / Registration Flow (May 16, 2026)

### Flow: 4 screens (approved)
1. Welcome — logo, value props, Get Started / Sign In
2. Vehicle — year, make, model, mileage, fuel type → estimated cost per mile
3. Preferences — region, primary platform, minimum net value threshold
4. Permissions — overlay (required), location (optional)
5. Account — optional, unlocks Pro features

### Vehicle screen (step 1) — approved design
Captures:
- Year (dropdown)
- Make (dropdown)
- Model (dropdown)
- Current mileage — affects maintenance cost estimate
- Fuel type — Gas / Electric / Hybrid

Cost per mile breakdown shown live:
- Fuel cost per mile (from EPA MPG data for vehicle)
- Wear cost per mile (tire/brake estimate)
- Maintenance cost per mile (age/mileage adjusted)
- Total replaces flat IRS $0.90 rate

### New DB table required: vehicle_profiles
Fields needed:
- id (PK)
- year (INT)
- make (TEXT)
- model (TEXT)
- current_mileage (INT)
- fuel_type (TEXT) — GAS | ELECTRIC | HYBRID
- epa_mpg_city (REAL)
- epa_mpg_highway (REAL)
- estimated_cost_per_mile (REAL) — computed, stored for scoring
- created_at (LONG)
- updated_at (LONG)

### New scorer_config keys needed:
- vehicle_fuel_cost_per_mile — from EPA MPG + local gas price
- vehicle_wear_cost_per_mile — tire/brake estimate
- vehicle_maintenance_cost_per_mile — age/mileage adjusted
- local_gas_price — set from region, adjustable in Settings

### EPA data source (free, no API key):
https://www.fueleconomy.gov/feg/ws/index.shtml
- /api/vehicle/menu/year — get available years
- /api/vehicle/menu/make?year=X — get makes for year
- /api/vehicle/menu/model?year=X&make=Y — get models
- /api/vehicle/menu/options?year=X&make=Y&model=Z — get trim options
- Returns MPG city/highway in JSON, free, no auth

### Implementation plan:
1. Add vehicle_profiles Room entity + DAO + migration
2. Create VehicleSetupActivity.kt (Compose) for onboarding screen
3. Create FuelEconomyApiClient.kt — calls fueleconomy.gov API
4. CostPerMileCalculator.kt — fuel + wear + maintenance formula
5. Wire calculated cost_per_mile to scorer_config on setup complete
6. Add vehicle info display to Settings screen

### Onboarding approved rendering: giglens_onboarding_vehicle

---

## FEATURE SPEC — Widget Gestures + Auto-Scan (May 16, 2026)

### Widget state progression (approved)
GigL (default) → capturing → scoring... → verdict pill

### Widget gesture map (approved)
- Default state: "GigL" label + "tap to scan" hint
- Short press: capture → OCR → score → display result
- Long press: context menu (scan now, history, move, settings, hide)
- Drag: move widget to any screen position
- Tap result pill: expand/collapse mini drawer
- Pull drawer: expand to full detail

### Long press context menu items:
1. Scan offer now — manual trigger
2. Offer history — opens history view
3. Move widget — enters drag mode with position saved to app_config
4. Settings — opens GigLens settings
5. Hide widget — collapses to notification tray

### Auto-scan feature (approved)
Optional — driver enables in Settings.
Default: OFF (Accessibility Service requires explicit user consent)

Three scan modes (mutually exclusive, stored in app_config):
- AUTO: OfferDetectorService watches DoorDash, fires on offer detection
  Requires: BIND_ACCESSIBILITY_SERVICE permission
  Widget animates automatically without driver interaction
- MANUAL: Driver taps widget pill to trigger scan
  Requires: SYSTEM_ALERT_WINDOW only
  Widget shows "tap to scan" hint
- SHARE: Driver screenshots → shares to GigLens (original mode)
  Requires: zero permissions
  Widget not shown — result appears as notification

New app_config keys:
- auto_scan_enabled: "true" | "false" (default: false)
- scan_mode: "AUTO" | "MANUAL" | "SHARE" (default: "MANUAL")

### Settings UI additions needed:
- Scan mode selector (radio: Auto / Manual / Share)
- Auto mode explanation dialog before enabling
  (explains Accessibility Service scope — DoorDash only)
- Widget position preference (saved position or reset to default)
- Widget label preference (show "GigL" or custom label)

### Implementation order:
1. Add scan_mode to app_config + AppConfigKeys
2. Add scan mode selector to SettingsActivity
3. Wire OfferDetectorService to check scan_mode before firing
4. Wire widget short press to trigger manual scan
5. Animate widget through states during capture/score pipeline
6. Save widget position to app_config on drag end

### Reference rendering: giglens_widget_gesture_states
