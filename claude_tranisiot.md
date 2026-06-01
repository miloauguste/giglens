# GigLens — Full Development Handoff Prompt
**Date:** May 26, 2026  
**Version:** v0.1.65 (versionCode 66)  
**Handoff from:** Claude Sonnet 4.6 (Anthropic)  
**Project owner:** Milo Auguste Jr. — Auguste Enterprise Holdings LLC  
**Repository:** `github.com/miloauguste/giglens` (private)  
**Build server:** `milo-dev` — Ubuntu 24, i9, 48GB RAM (`poppa@10.0.0.16`)  
**Project path:** `/home/poppa/giglens`

---

## ROLE

You are the lead Android developer on **GigLens** — an Android app that helps gig economy drivers (DoorDash focus) automatically capture, parse, score, and display delivery offer verdicts via a floating overlay pill. You are taking over from Claude Sonnet 4.6. Read this entire document before touching any code.

---

## PRODUCT OVERVIEW

GigLens runs as an Android Accessibility Service. When a delivery offer appears on the driver's screen:

1. `OfferDetectorService` detects offer keywords (Accept/Decline/$) via accessibility events
2. Signals `ScreenCaptureService` to take a screenshot via MediaProjection API
3. ML Kit OCR extracts: pay amount, mileage, restaurant name, deliver-by time
4. `OfferParser` parses the raw OCR text into structured `ParsedOffer`
5. `OfferScorer` calculates net value after vehicle costs + time costs
6. `OfferOverlayService` displays a floating pill with TAKE / BORDERLINE / SKIP verdict
7. Everything is saved to Room DB (`giglens_offers.db`)

**Alternative flow (manual):** Driver takes screenshot → shares to GigLens via Android share sheet → `ShareReceiverActivity` handles it → same OCR/score/overlay pipeline

---

## TECH STACK

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 (Android 14) |
| OCR | Google ML Kit `text-recognition:16.0.0` |
| Database | Room + SQLite (`giglens_offers.db`) |
| Overlay | Android `WindowManager` (`TYPE_APPLICATION_OVERLAY`) |
| Screen capture | `MediaProjectionManager` + `ImageReader` |
| Accessibility | `AccessibilityService` (`OfferDetectorService`) |
| Location | Google Play Services Location |
| Build system | Gradle (Kotlin DSL) — `build.gradle.kts` |
| DI | None — manual instantiation |
| Async | Kotlin Coroutines + `lifecycleScope` |

---

## DEVICE INVENTORY

| Device | IP | OS | Role |
|---|---|---|---|
| Pixel 10 Pro XL | `10.0.0.110:5555` | Android 16 (SDK 36) production | Primary test device |
| Samsung S20 (SM-G981V) | `10.0.0.189:5555` | Android 11 (SDK 30) | Secondary / share flow testing |

**Critical:** IPs were historically confused — always verify with:
```bash
adb -s 10.0.0.110:5555 shell getprop ro.product.model  # → Pixel 10 Pro XL
adb -s 10.0.0.189:5555 shell getprop ro.product.model  # → SM-G981V
```

---

## PROJECT STRUCTURE

```
/home/poppa/giglens/
├── app/src/main/
│   ├── java/com/augusteenterprise/giglens/
│   │   ├── ui/
│   │   │   ├── MainActivity.kt          ← main screen, MediaProjection request
│   │   │   ├── SettingsActivity.kt      ← capture mode, platform toggles
│   │   │   ├── ShareReceiverActivity.kt ← manual share flow (working)
│   │   │   └── OfferHistoryActivity.kt  ← offer log view
│   │   ├── service/
│   │   │   ├── OfferDetectorService.kt  ← Accessibility Service (core)
│   │   │   ├── OfferOverlayService.kt   ← floating pill widget
│   │   │   ├── ScreenCaptureService.kt  ← MediaProjection screenshot
│   │   │   ├── CaptureButtonService.kt  ← floating 📷 tap-to-capture button
│   │   │   ├── BootReceiver.kt          ← auto-start on boot
│   │   │   └── DebugTriggerReceiver.kt  ← ADB test harness (DEBUG ONLY)
│   │   ├── ocr/
│   │   │   ├── OfferParser.kt           ← ML Kit text → ParsedOffer
│   │   │   └── StreetExtractor.kt       ← address extraction from OCR
│   │   ├── scoring/
│   │   │   └── OfferScorer.kt           ← net value calculator
│   │   ├── data/
│   │   │   ├── AppConfig.kt / AppConfigDao.kt / AppConfigKeys.kt
│   │   │   ├── OfferCapture.kt / OfferCaptureDao.kt
│   │   │   ├── ScorerConfig.kt / ScorerConfigDao.kt / ScorerConfigKeys.kt
│   │   │   └── PlatformRegistry.kt      ← supported gig app package names
│   │   ├── geocoding/
│   │   │   └── GeocodingHelper.kt
│   │   └── location/
│   │       └── LocationHelper.kt
│   ├── res/
│   │   ├── xml/
│   │   │   └── accessibility_service_config.xml  ← OS-level security policy
│   │   └── layout/
│   │       ├── activity_main.xml
│   │       ├── activity_settings.xml
│   │       └── activity_offer_history.xml
│   └── AndroidManifest.xml
├── app/build.gradle.kts                 ← Kotlin DSL build file
├── giglens-release.keystore             ← NEVER COMMIT — Play Store signing key
├── CHANGELOG.md                         ← updated every commit
└── SECURITY.md                          ← MobSF false positives documented
```

---

## CORE FILE DETAILS

### `OfferDetectorService.kt`
- Extends `AccessibilityService`
- Package allowlist: `com.doordash.driverapp` only (Phase 1)
- Fingerprint dedup: prevents re-triggering on same static offer screen
- Cooldown: 3000ms between captures
- **Android 16 fix:** `onServiceConnected()` calls `setServiceInfo()` programmatically — XML config alone insufficient on SDK 36
- Checks `auto_capture_mode` from DB — skips if `off` or `button`
- Sends `ACTION_OFFER_DETECTED` broadcast to `ScreenCaptureService`

### `OfferParser.kt`
- `isOfferScreen()` — requires ≥2 keyword matches + dollar amount
- `extractPay()` — handles `$` and OCR misread `S` prefix
- `extractDistance()` — regex for `X.X mi` / `miles`
- `extractRestaurant()` — **Bug E FIXED May 25**:
  - Strategy 1: finds line after `@ Pickup`, strips leading OCR noise (`| McDonald's` → `McDonald's`)
  - Strategy 2: fallback scan with status word denylist (busy, popular, trending, etc.)
- `extractDeliverByMinutes()` — parses `Deliver by 3:17 PM` → minutes since midnight
- `extractCurrentTime()` — reads status bar first line

### `OfferScorer.kt`
- Formula: `net_value = pay - (total_miles × cost_per_mile) - (minutes_on_job / 60 × hourly_rate)`
- Weighted score: net value (50%) + pickup penalty (30%) + true $/mi (20%)
- Floor check: net ≤ 0 OR pay/mi < $1.50 OR total pay < $6.00 → forced SKIP
- Thresholds: TAKE ≥ 65, BORDERLINE 40–64, SKIP < 40
- Configurable via `ScorerConfig` DB table

### `ScreenCaptureService.kt`
- Requires active `MediaProjection` session (granted at runtime via dialog)
- Listens for `ACTION_OFFER_DETECTED` broadcast
- Captures screen → saves PNG → runs ML Kit OCR → scores → launches overlay
- **Full pipeline wired May 26** — previously only saved to DB

### `OfferOverlayService.kt`
- `TYPE_APPLICATION_OVERLAY` window over all apps
- States: IDLE → CAMERA → PROCESSING → PILL (with verdict)
- Countdown timer: configurable display seconds
- `isOverlayAdded` flag prevents duplicate `WindowManager.addView()` calls (Bug #1 fix)

### `CaptureButtonService.kt`
- Floating draggable 📷 button over supported gig apps
- Tap → broadcasts `ACTION_OFFER_DETECTED` → triggers `ScreenCaptureService`
- Tap flash: teal → green → teal (300ms)
- Requires: `auto_capture_mode = "button"` or `"both"` AND MediaProjection active

### `accessibility_service_config.xml`
```xml
android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
android:accessibilityFeedbackType="feedbackGeneric"
android:accessibilityFlags="flagDefault|flagReportViewIds"
android:canRetrieveWindowContent="true"
android:packageNames="com.doordash.driverapp"
android:isAccessibilityTool="true"
android:notificationTimeout="500"
```

---

## DATABASE SCHEMA

**DB name:** `giglens_offers.db`

**Tables:**
- `app_config` — key/value settings (auto_capture_mode, enabled_platforms, gps_enabled, etc.)
- `offer_captures` — every offer logged (pay, distance, restaurant, verdict, score, net_value, screenshot_path, raw_ocr_text, etc.)
- `scorer_config` — scoring weights and thresholds

**Query DB on device:**
```bash
adb -s 10.0.0.110:5555 shell "run-as com.augusteenterprise.giglens cat databases/giglens_offers.db" > /tmp/giglens.db && sqlite3 /tmp/giglens.db "SELECT * FROM app_config;"
```

---

## DEVELOPMENT WORKFLOW — NON-NEGOTIABLE RULES

1. **ALL changes to QA first** — there is no separate QA environment here; test on device before commit
2. **Build must pass** `./gradlew assembleDebug` before committing
3. **`py_compile` equivalent** — Kotlin compile check: `./gradlew compileDebugKotlin`
4. **CHANGELOG.md** updated in same commit as code — no exceptions
5. **Never manually bump version** — pre-commit hook auto-increments `versionCode` and `versionName`
6. **Every Kotlin file header** must identify which LLM wrote/modified it and date
7. **Git workflow:** feature branch → test on device → commit → push
8. **Install on BOTH phones** after every build (unless device-specific fix)
9. **Full paths always** — never relative paths in commands
10. **CHANGELOG format:**
```
## [0.1.66] - 2026-05-26
### Fixed
- Bug E: restaurant extraction now strips OCR prefix noise
```

**Build command:**
```bash
cd /home/poppa/giglens && ./gradlew assembleDebug 2>&1 | tail -20
```

**Install both phones (only if build succeeds):**
```bash
cd /home/poppa/giglens && ./gradlew assembleDebug 2>&1 | tail -3 && \
{ grep -q "BUILD SUCCESSFUL" <(./gradlew assembleDebug 2>&1) && \
  adb -s 10.0.0.110:5555 install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb -s 10.0.0.189:5555 install -r app/build/outputs/apk/debug/app-debug.apk; } || \
echo "BUILD FAILED — install skipped"
```

**Commit pattern:**
```bash
cd /home/poppa/giglens && git add <files> && git commit -m "Component: description of change"
```

---

## SECURITY ARCHITECTURE

### Accessibility Service Security Gates (5 layers)
1. **OS-level:** `android:packageNames` in XML — Android drops events from non-allowlisted packages before Kotlin runs
2. **Code-level:** `SUPPORTED_PACKAGES` set check — first line of `onAccessibilityEvent()`
3. **Content gate:** `isOfferScreen()` surface text check — before node traversal
4. **No raw logging in release:** all `Log.d` with screen content wrapped in `BuildConfig.DEBUG`
5. **Data minimization:** only structured fields written to SQLite — never raw screen text

### Pre-commit Hook (automatic on every commit)
- Android Lint
- Gitleaks — no secrets in source or res files
- Hardcoded value scan
- APK permissions audit (currently: 11 declared — all legitimate)

### Pre-push Hook (automatic on every push)
- OWASP dependency CVE scan
- Semgrep static analysis
- Unit tests

### On-demand scans
```bash
DEEP_SCAN=1 git commit --allow-empty -m "chore: security scan"
MOBSF_SCAN=1 git commit --allow-empty -m "chore: mobsf scan"
```

### MobSF Baseline
- Debug build score: 46/100 (ceiling ~47 — expected for debug)
- Release build target: ~75/100
- False positives documented in `SECURITY.md`
- MobSF running on `http://localhost:8010` (Docker)

### Key Security Rules
- `DebugTriggerReceiver` is `exported="true"` — **MUST be wrapped in `BuildConfig.DEBUG` before Play Store release**
- `giglens-release.keystore` — **NEVER commit to git** — stored at `/home/poppa/giglens/giglens-release.keystore` and backed up at `~/giglens-release.keystore.backup`
- Keystore alias: `giglens`, org: Auguste Enterprise Holdings LLC
- Signing passwords stored as env vars: `GIGLENS_STORE_PASSWORD`, `GIGLENS_KEY_PASSWORD`

---

## DEBUGGING TOOLS

### ADB Debug Trigger (test widget without live offer)
```bash
# Simulate offer detected (camera state)
adb -s 10.0.0.110:5555 shell am broadcast \
  -a com.augusteenterprise.giglens.DEBUG_TRIGGER \
  -n com.augusteenterprise.giglens/.service.DebugTriggerReceiver \
  --es state camera

# Simulate scored offer (pill state)
adb -s 10.0.0.110:5555 shell am broadcast \
  -a com.augusteenterprise.giglens.DEBUG_TRIGGER \
  -n com.augusteenterprise.giglens/.service.DebugTriggerReceiver \
  --es state offer

# Hide widget
adb -s 10.0.0.110:5555 shell am broadcast \
  -a com.augusteenterprise.giglens.DEBUG_TRIGGER \
  -n com.augusteenterprise.giglens/.service.DebugTriggerReceiver \
  --es state hide
```

### Logcat (GigLens PID only)
```bash
adb -s 10.0.0.110:5555 logcat --pid=$(adb -s 10.0.0.110:5555 shell pidof com.augusteenterprise.giglens | tr -d '\r') | grep -E "(OfferDetector|ScreenCapture|OfferParser|Parsed|Score|Verdict|restaurant)"
```

### Check accessibility service status
```bash
adb -s 10.0.0.110:5555 shell settings get secure enabled_accessibility_services
adb -s 10.0.0.110:5555 shell dumpsys accessibility | grep -A5 "GigLens"
```

### Re-enable accessibility service (ADB — use when toggling from settings resets it)
```bash
adb -s 10.0.0.110:5555 shell settings put secure enabled_accessibility_services \
  "com.mdiwebma.screenshot/.service.MyAccessibilityService:com.augusteenterprise.giglens/com.augusteenterprise.giglens.service.OfferDetectorService"
```

### Query DB
```bash
adb -s 10.0.0.110:5555 shell "run-as com.augusteenterprise.giglens cat databases/giglens_offers.db" > /tmp/gl.db && sqlite3 /tmp/gl.db "SELECT key, value FROM app_config;"
```

### Pull saved screenshots
```bash
adb -s 10.0.0.110:5555 shell ls /storage/emulated/0/Android/data/com.augusteenterprise.giglens/files/GigLens/screenshots/
adb -s 10.0.0.110:5555 pull /storage/emulated/0/Android/data/com.augusteenterprise.giglens/files/GigLens/screenshots/offer_XXXX.png /tmp/
```

---

## KNOWN BUGS — FIXED

| Bug | Description | Fix | Date |
|---|---|---|---|
| Bug #1 | Duplicate overlay views — `addView()` called on every offer | `isViewAdded` flag in `OfferOverlayService` | May 23 |
| Bug E | `restaurant=Busy` instead of actual name | `cleanLine()` strips OCR prefix noise (`\|`, `@`); status word denylist | May 25 |
| Settings orange warning never disappears | `isAccessibilityEnabled()` checked short path only | Check both short and full path forms | May 26 |
| Settings doesn't refresh after returning from system settings | No `onResume()` | Added `onResume()` → `updateAccessibilityPermUI()` | May 26 |
| DoorDash package name wrong | `com.dd.doordash` used everywhere | Corrected to `com.doordash.driverapp` | May 26 |
| `ScreenCaptureService` never scored or showed overlay | `runOcr()` only saved to DB | Full pipeline wired: parse → score → DB → overlay | May 26 |

---

## KNOWN BLOCKERS

### BLOCKER 1 — Android 16 Accessibility Event Delivery (CRITICAL)
**Device:** Pixel 10 Pro XL (`10.0.0.110`) — Android 16 SDK 36, production build  
**Symptom:** `OfferDetectorService` connects successfully but `onAccessibilityEvent()` never fires for any app — zero events delivered  
**Root cause:** Android 16 production builds on Pixel restrict accessibility event delivery to sideloaded (ADB-installed) APKs. Only apps installed via Play Store receive events on production Android 16 builds.  
**Evidence:**
- Service shows as enabled in `dumpsys accessibility`
- DoorDash visible in window list (`mAccessibilityWindowTitle=DoorDash`)
- Zero `OfferDetector` log lines despite DoorDash being on screen
- `isAccessibilityTool="true"` added to config — did not resolve
- Runtime `setServiceInfo()` added in `onServiceConnected()` — did not resolve
- Battery optimization excluded — did not resolve
- Package filter removed entirely — did not resolve
**Workaround:** Use S20 (`10.0.0.189`) for auto-capture testing (Android 11, works correctly)  
**Resolution:** Publish to Play Store internal testing track → install via Play Store link → Android 16 trusts Play Store-installed apps

### BLOCKER 2 — MediaProjection Permission Not Triggered in MainActivity
**Symptom:** Tapping "Enable Screen Capture" button in MainActivity shows dialog but screen recording permission dialog never appears  
**Status:** In progress at time of handoff  
**Last state:** `showAutoModeDialog()` updated to call `requestScreenCapturePermission()` which calls `mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())` — but dialog not appearing on Pixel  
**Suspected cause:** Android 16 may require the MediaProjection request to come from a user gesture in a visible Activity. The Accessibility Service being active may interfere.  
**Next step:** Test on S20 first — if it works there, the Pixel issue is Android 16 specific

### BLOCKER 3 — Play Store Submission Incomplete
**Status:** AAB built and signed, keystore created, Play Console account needed  
**Keystore:** `/home/poppa/giglens/giglens-release.keystore` (alias: `giglens`)  
**Built AAB:** `/home/poppa/giglens/app/build/outputs/bundle/release/app-release.aab`  
**Next steps:**
1. Create Google Play Console account (`https://play.google.com/console`) — $25 one-time fee
2. Create app listing (Internal Testing track — never goes public unless explicitly published)
3. Upload `app-release.aab`
4. Add test device Gmail accounts to tester list
5. Install via Play Store private link on Pixel 10 Pro XL
6. Re-test auto-capture — should work once installed via Play Store

---

## IMMEDIATE NEXT STEPS (priority order)

### 1. Fix MediaProjection Dialog (URGENT — in progress)
**Goal:** Tapping the button in MainActivity triggers the "GigLens wants to capture your screen" dialog  
**File:** `MainActivity.kt` — `showAutoModeDialog()` and `requestScreenCapturePermission()`  
**Test:** Force stop → relaunch → tap button → dialog should appear  
**If broken on Pixel:** test on S20 first to isolate Android 16 vs code issue

### 2. Publish to Play Store Internal Testing
**Goal:** Install GigLens via Play Store on Pixel 10 Pro XL to bypass Android 16 sideload restriction  
**Steps:** Create Play Console account → upload signed AAB → invite test Gmail → install via private link  
**Expected outcome:** Auto-capture pipeline fires on live DoorDash offers on Pixel

### 3. Test Full Auto-Capture Pipeline with Live Offer
**Goal:** Open DoorDash on S20 or Play-Store-installed Pixel → offer appears → GigLens auto-detects → pill shows verdict  
**Watch for:**
```
I OfferDetector: NEW offer screen detected — signaling capture
D ScreenCapture: OCR result (XXX chars)
D OfferParser: Parsed offer: pay=X.XX distance=X.X restaurant=XXXX
I ShareReceiver: Score: XX | Verdict: TAKE/BORDERLINE/SKIP
D OfferOverlayService: Result: verdict=TAKE net=X.XX
```

### 4. Wire Floating Button → MediaProjection → Capture
**Goal:** Driver taps 📷 button → screenshot taken → OCR → score → pill  
**Dependency:** MediaProjection permission must be granted first (Blocker 2)  
**Mode setting:** `auto_capture_mode = "button"` or `"both"` in Settings

### 5. PROCESSING State (UX)
**Goal:** Widget shows "Processing..." between offer detection and score display  
**File:** `OfferOverlayService.kt` — add `SHOW_PROCESSING` action handler  
**In `ShareReceiverActivity`:** send `SHOW_PROCESSING` intent before OCR starts

### 6. OfferPipeline.kt Refactor
**Goal:** Extract duplicated OCR → parse → score → overlay logic from `ShareReceiverActivity` and `ScreenCaptureService` into shared `OfferPipeline.kt`  
**~60 lines of duplication** across both classes  
**Risk:** Medium — both currently working, refactor carefully

### 7. DebugTriggerReceiver — Release Safety
**Goal:** Wrap `DebugTriggerReceiver` in `BuildConfig.DEBUG` so it's excluded from release builds  
**File:** `AndroidManifest.xml`
```xml
<receiver
    android:name=".service.DebugTriggerReceiver"
    android:exported="${debugMode}"
    tools:replace="android:exported">
```
Or use `debug/AndroidManifest.xml` overlay

---

## UPCOMING FEATURES (BACKLOG)

| Feature | Description | Priority |
|---|---|---|
| PROCESSING state | Widget shows processing between detect and pill | High |
| Restaurant extraction hardening | Test with more DoorDash UI variants | High |
| OfferPipeline.kt | Eliminate code duplication | Medium |
| Onboarding flow | First-run permissions walkthrough | Medium |
| Platform selector UI | Toggle Uber Eats, Grubhub when ready | Medium |
| Offer detail view | Tap offer in log → full OCR + score breakdown | Medium |
| CSV export | Session data for driver tax/mileage records | Low |
| Multi-platform OCR profiles | Per-platform keyword sets in OfferParser | Low |
| Community data backend | Aggregate offer data across drivers | Future |
| Compose migration (v2.0) | Migrate from XML views to Jetpack Compose | Future |

---

## CODING STANDARDS

### File Header (required on every Kotlin file)
```kotlin
package com.augusteenterprise.giglens.service

// Author: Claude Sonnet 4.6 (Anthropic) - May 26 2026
// Description: What this file does
// Last modified: [LLM name] - [date] - [what changed]
```

### Inline Comments
- Every non-obvious block must have a comment
- Use CORRECT/WRONG pattern for critical logic:
```kotlin
// CORRECT: call addView() once, then updatePillContent() on subsequent offers
// WRONG:   calling addView() every time — stacks invisible views
```

### Coroutines
- Use `lifecycleScope` in Activities/Fragments
- Use `serviceScope` (SupervisorJob + Dispatchers.IO) in Services
- Never use `GlobalScope`
- Always cancel jobs in `onDestroy()`

### Logging
```kotlin
// Debug only — never log sensitive content in release
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Raw OCR: $rawText")  // NEVER in release
}
Log.i(TAG, "Parsed offer: pay=$pay restaurant=$restaurant")  // OK in release
```

### Node recycling (Accessibility)
```kotlin
val root = rootInActiveWindow ?: return
try {
    processNode(root)
} finally {
    root.recycle()  // Always recycle — prevents memory leaks
}
```

### Python anchor replacements
When editing files via Python `str.replace()`:
- Always use `cat -A` first to check for hidden chars (`\r`, unicode bullets)
- If anchor fails, use line-number based replacement instead
- Always verify the file looks correct after replacement before building

---

## TESTING CHECKLIST

### Before every commit
- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew compileDebugKotlin` no errors
- [ ] Install on both phones
- [ ] Debug trigger fires overlay on both phones
- [ ] Share flow works on S20 (`restaurant=` shows correct name, not "Busy")
- [ ] CHANGELOG.md updated

### Full pipeline test
- [ ] Share DoorDash screenshot → `Parsed offer: pay=X restaurant=X` in logcat
- [ ] Score appears: `Score: XX | Verdict: TAKE/BORDERLINE/SKIP`
- [ ] Overlay pill appears with correct verdict
- [ ] Countdown timer shows and dismisses

### Security test (before release)
- [ ] `DEEP_SCAN=1 git commit` — OWASP + MobSF clean
- [ ] `DebugTriggerReceiver` removed or gated by `BuildConfig.DEBUG`
- [ ] No hardcoded passwords or API keys in source
- [ ] `giglens-release.keystore` not committed to git

---

## PLATFORM REGISTRY

| Platform | Package name | Status |
|---|---|---|
| DoorDash Driver | `com.doordash.driverapp` | ✅ Active Phase 1 |
| Uber Eats Driver | `com.ubereats.driver` | Planned Phase 2 |
| Grubhub Driver | `com.grubhub.driver` | Planned Phase 2 |
| Instacart Shopper | `com.instacart.shopper` | Planned Phase 3 |
| Amazon Flex | `com.amazon.rabbit` | Planned Phase 3 |
| Shipt Shopper | `com.shipt.shopper` | Planned Phase 3 |

**To add a new platform:**
1. Add package to `accessibility_service_config.xml` `android:packageNames`
2. Add to `SUPPORTED_PACKAGES` set in `OfferDetectorService.kt`
3. Add to `PlatformRegistry.kt`
4. Add keyword profile to `OfferParser.kt` `OFFER_KEYWORDS`
5. Add `detectPlatform()` case in `ShareReceiverActivity.kt` and `ScreenCaptureService.kt`

---

## PLAY STORE STATUS

**Account:** Not yet created  
**Developer registration:** $25 one-time (https://play.google.com/console)  
**Distribution model:** Internal Testing track — private, never publicly listed, no Google fees for free apps  
**Signed AAB:** `/home/poppa/giglens/app/build/outputs/bundle/release/app-release.aab`  
**Keystore:** `/home/poppa/giglens/giglens-release.keystore`  
**Alias:** `giglens`  
**Signing passwords:** Environment variables `GIGLENS_STORE_PASSWORD` and `GIGLENS_KEY_PASSWORD`  

**Build release AAB:**
```bash
export GIGLENS_STORE_PASSWORD='your_password' && \
export GIGLENS_KEY_PASSWORD='your_password' && \
cd /home/poppa/giglens && ./gradlew bundleRelease 2>&1 | tail -5
```

---

## CURRENT APP STATE (as of May 26, 2026 ~11PM)

| Component | Status | Notes |
|---|---|---|
| Share flow (manual) | ✅ Working | S20 confirmed, Pixel confirmed |
| OCR parsing | ✅ Working | `restaurant=McDonald's` confirmed |
| Scoring | ✅ Working | net value, verdict, all fields |
| Overlay pill | ✅ Working | debug trigger confirmed both phones |
| Accessibility Service | ✅ Enabled | Both phones |
| Auto-capture (accessibility mode) | ❌ Blocked | Android 16 sideload restriction on Pixel; S20 untested with live offer |
| Tap-to-capture (floating button) | ⚠ In progress | MediaProjection dialog not triggering |
| MediaProjection permission | ❌ Not granted | Dialog not appearing on Pixel |
| Settings green Active indicator | ✅ Working | Shows after accessibility enabled |
| Settings orange warning | ✅ Fixed | Disappears when service enabled |
| Play Store | ⚠ In progress | AAB built, account not yet created |

---

## WHAT THE PREVIOUS AI DID LAST

The last action before handoff was fixing `MainActivity.kt` — specifically `showAutoModeDialog()` and `updateUI()` to properly request MediaProjection permission when the driver taps the capture button. The build succeeded and was installed on the Pixel, but the screen recording dialog is not appearing when the button is tapped.

**The file being actively worked on:** `MainActivity.kt`  
**The immediate next debugging step:** Get GigLens PID on Pixel and watch logcat while tapping the button to see if `showAutoModeDialog()` is even being called and why `mediaProjectionLauncher.launch()` isn't producing a dialog.

```bash
adb -s 10.0.0.110:5555 shell pidof com.augusteenterprise.giglens
# Use the PID returned above:
adb -s 10.0.0.110:5555 logcat --pid=XXXX | grep -E "(showAutoMode|requestScreen|MediaProjection|Projection|btnToggle|dialog)"
```

Then tap the button on the Pixel.
