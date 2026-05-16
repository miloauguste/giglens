# GigLens — DeepSeek Session Handoff
# Generated: May 16, 2026 01:42 PM
# Author: Claude (Anthropic) → handoff to DeepSeek

---

## CRITICAL RULES (read before touching any file)

1. All changes go to /home/poppa/giglens/app/src/ only
2. NEVER hardcode any value — all config lives in scorer_config or app_config DB tables
3. NEVER use sed or nano for edits — use Python replace pattern only
4. NEVER edit prod — dev is /home/poppa/giglens (same repo, single environment)
5. Every Python file header must include Author field identifying DeepSeek wrote it
6. Every commit must update CHANGELOG.md
7. Run security scan after every session: bash /home/poppa/giglens/security_scan.sh
8. Build command: cd /home/poppa/giglens && ./gradlew assembleDebug
9. Install command: adb install -r app/build/outputs/apk/debug/app-debug.apk
10. Version bumps automatically on git commit — never manually edit APP_VERSION

## CORRECT Python edit pattern:
```python
path = '/home/poppa/giglens/app/src/...'
with open(path) as f: content = f.read()
content = content.replace('OLD', 'NEW')
with open(path, 'w') as f: f.write(content)
print('Done')
```

## WRONG — never do this:
```bash
sed -i 's/old/new/' file.kt   # WRONG
nano file.kt                   # WRONG
echo "hardcoded value"         # WRONG
```

---

## PROJECT CONTEXT

GigLens is an Android app for gig drivers (DoorDash first).
Share-to-capture working end-to-end at v0.1.22.
Full scoring engine live: net value = offer_pay - (total_miles x cost_per_mile).

Key paths:
- Project: /home/poppa/giglens/
- Source: /home/poppa/giglens/app/src/main/java/com/augusteenterprise/giglens/
- Layout: /home/poppa/giglens/app/src/main/res/layout/
- DB: giglens_offers.db (Room, v4)
- Session doc: /home/poppa/giglens/gig_summary.md (read this for full context)

Device: Samsung Galaxy S20 via ADB (R3CN509D8WX)
Build server: milo-dev, headless Linux

---

## CURRENT DB SCHEMA (v4)

Tables:
- offer_captures — all scored offers
- scorer_config — numeric weights/thresholds (all tunable)
- app_config — string settings (region, scan_mode, etc.)
- vehicle_profiles — PLANNED, not yet built

Key scorer_config values:
- cost_per_mile: 0.90 (vehicle cost per mile driven)
- weight_pickup_penalty: 0.50
- weight_true_pay_per_mile: 0.20
- weight_total_pay_v2: 0.20
- threshold_take: 65.0
- threshold_borderline: 40.0

Key app_config keys:
- driver_region, driver_manual_region, gps_enabled
- scan_mode: AUTO | MANUAL | SHARE (default: MANUAL)
- auto_scan_enabled: true | false (default: false)
- two_fa_enabled: true | false (default: false)
- widget_x, widget_y (planned — widget position)

---

## BUILD TASKS FOR THIS SESSION (priority order)

### TASK 1 — vehicle_profiles DB table
Create Room entity, DAO, migration v4→v5.
Fields: id, year, make, model, current_mileage, fuel_type,
        epa_mpg_city, epa_mpg_highway, estimated_cost_per_mile,
        created_at, updated_at
fuel_type enum: GAS | ELECTRIC | HYBRID
After creating table, wire estimated_cost_per_mile to scorer_config cost_per_mile.

### TASK 2 — FuelEconomyApiClient.kt
Free API, no key needed: https://www.fueleconomy.gov/feg/ws/index.shtml
Endpoints:
- GET /api/vehicle/menu/year → list of years
- GET /api/vehicle/menu/make?year=X → list of makes
- GET /api/vehicle/menu/model?year=X&make=Y → list of models
- GET /api/vehicle/menu/options?year=X&make=Y&model=Z → MPG data
Returns JSON. Parse mpgCity and mpgHwy. Store in vehicle_profiles.
Location: /home/poppa/giglens/app/src/main/java/com/augusteenterprise/giglens/vehicle/FuelEconomyApiClient.kt

### TASK 3 — CostPerMileCalculator.kt
Formula:
  fuel_cost = (gas_price_per_gallon / mpg_combined) where mpg_combined = (mpgCity + mpgHwy) / 2
  wear_cost = 0.045 per mile (tire/brake estimate — stored in scorer_config)
  maintenance_cost = base 0.035 + (0.005 per 10k miles over 50k) — stored in scorer_config
  total = fuel_cost + wear_cost + maintenance_cost
All rates must come from scorer_config DB — never hardcoded.
Location: /home/poppa/giglens/app/src/main/java/com/augusteenterprise/giglens/vehicle/CostPerMileCalculator.kt

### TASK 4 — OfferOverlayService.kt
Floating overlay widget using WindowManager.
Permission required: SYSTEM_ALERT_WINDOW (already in manifest? check first)
Widget states (Compose):
  PILL: shows "GigL" by default, verdict + score after scan
  MINI: tap pill → slides open mini drawer (one-liner)
  FULL: pull drawer → full breakdown
Gestures:
  Short press → trigger scan (calls ShareReceiverActivity pipeline)
  Long press → context menu (scan now, history, move, settings, hide)
  Drag → move widget, save position to app_config widget_x/widget_y
Position: top-right by default, user-movable anywhere
Accept button must NEVER be obscured at any widget state.

### TASK 5 — Onboarding screens (Compose)
4 screens using Scaffold + bottomBar pattern (sticky buttons):
  Screen 1: Welcome (logo, value props, get started / sign in)
  Screen 2: Vehicle (year/make/model/mileage/fuel — calls FuelEconomyApiClient)
  Screen 3: Preferences (region, platform, min net value)
  Screen 4: Permissions (overlay required, location optional)
  Screen 5: Account (optional, email + password, 2FA mention)
Rule: content in LazyColumn, buttons in bottomBar slot, imePadding() on all inputs.
No horizontal scroll anywhere. OTP boxes use weight(1f) + aspectRatio(1f).

### TASK 6 — 2FA OTP screen
Email OTP only. 6-digit code. 10 min expiry.
3 attempts before 5-min lockout. 60s resend cooldown.
OTP boxes: Row with weight(1f) per box — fluid width, no fixed px.
States: waiting, wrong code, locked out.
Sticky bottom: Verify button + Resend always visible.
New app_config keys: two_fa_enabled, two_fa_method.

### TASK 7 — Fix pre-commit lint integer comparison warning
File: /home/poppa/giglens/.git/hooks/pre-commit line 37
Error: [: 0\n0: integer expression expected
The lint error count grep is returning multiline string.
Fix: use grep -c piped through head -1 or use wc -l approach.

---

## SECURITY REQUIREMENTS (enforce on every task)

- Run bash /home/poppa/giglens/security_scan.sh before ending session
- All new API calls must use HTTPS only
- No API keys in source code — store in app_config DB
- No hardcoded strings in business logic
- All new DB tables need Room migration (never destructive)
- All exceptions must be explicitly logged — no bare catch blocks
- Screenshot storage: use getFilesDir() not getExternalFilesDir()

---

## TESTING PROTOCOL

After each task:
1. ./gradlew assembleDebug — must pass with 0 errors
2. adb install -r app/build/outputs/apk/debug/app-debug.apk
3. adb logcat -d | grep "FATAL EXCEPTION" | tail -5 — must be empty
4. git add -A && git commit -m "descriptive message"

---

## QUICK REFERENCE

```bash
# Build
cd /home/poppa/giglens && ./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logs
adb logcat -T 1 | grep -E "ShareReceiver|OfferParser|GeocodingHelper|OfferScorer"

# Crash check
adb logcat -d | grep -A5 "FATAL EXCEPTION" | head -20

# Security scan
bash /home/poppa/giglens/security_scan.sh

# Git
cd /home/poppa/giglens && git add -A && git commit -m "message" && git push origin main

# DB inspection
adb exec-out run-as com.augusteenterprise.giglens cat /data/data/com.augusteenterprise.giglens/databases/giglens_offers.db > /tmp/giglens.db && adb exec-out run-as com.augusteenterprise.giglens cat /data/data/com.augusteenterprise.giglens/databases/giglens_offers.db-wal > /tmp/giglens.db-wal && sqlite3 /tmp/giglens.db "SELECT * FROM scorer_config;"
```

---

## SESSION END CHECKLIST

Before ending:
- [ ] All tasks built and tested
- [ ] 0 compile errors
- [ ] 0 FATAL EXCEPTIONS in logcat
- [ ] Security scan passing all Level 1 and Level 2 checks
- [ ] All changes committed and pushed to origin main
- [ ] gig_summary.md updated with what was done
- [ ] CHANGELOG.md updated

Read /home/poppa/giglens/gig_summary.md for full design decisions,
approved UI specs, and architectural rules before starting any task.
