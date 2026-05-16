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
