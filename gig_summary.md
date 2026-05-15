# GigLens — Session Summary (May 14-15, 2026)

---

## WHAT WAS DONE

### Development Environment Setup
1. **Android SDK installed on `milo-dev`** (headless Linux server)
   - Java 17 installed and set as default (was Java 8)
   - Android SDK command-line tools, platform-tools, build-tools 34.0.0, platforms android-34
   - Gradle 8.7 wrapper configured
   - Environment variables in `~/.bashrc`: `ANDROID_HOME`, PATH for SDK tools

2. **Samsung Galaxy S20 connected via ADB**
   - USB debugging enabled (Developer Options → USB Debugging)
   - udev rules added for Samsung vendor ID `04e8` at `/etc/udev/rules.d/51-android.rules`
   - Device confirmed: `R3CN509D8WX`

3. **Project scaffolded** — 8 Kotlin source files, Gradle build files, manifest, resources
   - Package: `com.augusteenterprise.giglens`
   - Path: `/home/poppa/giglens/`
   - GitHub: `miloauguste/giglens` (pushed, current version 0.1.1)

### Build Issues Fixed
4. **Room `OfferDatabase_Impl` crash** — `annotationProcessor` doesn't work with Kotlin. Changed to `kapt` with plugin declared in both top-level and app-level build files.

5. **Missing launcher icon** — Changed manifest to use `@android:drawable/sym_def_app_icon` as placeholder.

6. **Lint security error fixed** — `registerReceiver` missing `RECEIVER_NOT_EXPORTED` flag. Replaced with `ContextCompat.registerReceiver()`.

### Capture Architecture Decisions
7. **Three capture modes established (priority order):**
   - **Default: Share-to-capture** — zero permissions, driver screenshots offer → shares to GigLens
   - **Opt-in: Auto mode** — Accessibility Service, explained via dialog before permission request
   - **Optional: Notification listener** — moderate permission, but notification format is unreliable

8. **Share-to-capture pipeline built and tested:**
   - `ShareReceiverActivity` receives images via Android share sheet
   - ML Kit OCR extracts text on-device
   - `OfferParser` identifies offer screens and extracts pay, distance, restaurant
   - Results saved to Room database
   - Toast shows extracted summary to user

### OCR Parser Tuned with Real DoorDash Data
9. **`$` → `S` OCR misread fixed** — ML Kit reads `$4.70` as `S4.70`. Added secondary regex `(?<![A-Za-z])S(\d{1,3}\.\d{2})` requiring decimal point to avoid false positives like `PYS66`.

10. **Restaurant extraction improved** — Was grabbing map garbage ("Waltc") instead of "Panera Bread". Fixed by scanning 3 lines after "Pickup" keyword with quality filters (min 3 chars, starts uppercase, contains letters, not a UI element or street name).

11. **Pay amount false positive fixed** — `PYS66` was matching as $66.00. Split into two regex patterns: real `$` (any format) and OCR `S` (requires decimal point).

### Verified End-to-End with Real DoorDash Offer
12. **Test result — real DoorDash screenshot:**
    - Pay: $4.70 ✅
    - Distance: 2.7 mi ✅
    - Restaurant: Panera Bread ✅
    - Saved to Room DB with id=4

### Infrastructure
13. **Version auto-bumping** — Pre-commit hook bumps `version.txt`, `versionName`, and `versionCode` on every commit.

14. **Android Lint passes** — 0 errors, 31 warnings (all minor: outdated deps, cosmetic).

15. **CLAUDE.md system prompt created** — Full project context including architecture, OCR rules, security practices, roadmap, and quick reference commands.

16. **Git repo pushed** to `miloauguste/giglens`, branch `main`, version 0.1.1.

---

## WHAT'S LEFT TO DO

### Immediate (Next Session)
1. **OWASP Dependency Check** — Plugin added but NVD database download fails. Need to pass API key via command line:
   ```bash
   NVD_KEY=$(grep "owasp.nvd.api.key" /home/poppa/giglens/local.properties | cut -d= -f2)
   cd /home/poppa/giglens && ./gradlew :app:dependencyCheckAnalyze -Powasp.nvd.api.key="$NVD_KEY"
   ```
   The `dependencyCheck` block in `app/build.gradle.kts` currently has a minimal config (`failBuildOnCVSS = 7.0f`). The NVD API key is in `local.properties` (gitignored).

2. **Floating status widget** — Drivers want a visible indicator that GigLens is active. Needs `SYSTEM_ALERT_WINDOW` permission and a floating overlay service.

3. **Offer history view** — RecyclerView list of captured offers with date filters. `OfferCaptureDao` already has `getRecent()` and `getByDateRange()` queries.

4. **Multi-platform OCR** — Collect sample screenshots from Uber Eats, Grubhub, Instacart, Amazon Flex. Build per-platform parsing rules in `OfferParser.kt`.

5. **UI design pass** — Current UI is basic LinearLayout. Needs dark theme polish, custom app icon, onboarding flow, Material cards for stats.

### Phase 2 Features (Beta-Ready)
6. **Pay-per-mile color coding** — Green (good), yellow (ok), red (bad) on each offer
7. **Daily/weekly earnings summary** — Total pay, total miles, effective hourly rate
8. **User authentication** — Email + password, EncryptedSharedPreferences for tokens, JWT auth
9. **Room database encryption** — SQLCipher integration for user data protection

### Phase 3 Features (Analytics)
10. **Best hours heatmap** — "You earn the most Tuesdays 5-8pm"
11. **Restaurant ranking** — Which restaurants give the best offers
12. **Goal tracker** — "Make $150 today" progress bar
13. **End-of-day notification recap**
14. **CSV export** for tax records
15. **Decline reason logging**

### Phase 4-5 (Growth & Business)
16. **Cross-platform offer comparison**
17. **Smart alerts** ("This offer is 40% above your average")
18. **Community stats** (anonymized, requires backend)
19. **Fleet operator web dashboard**
20. **White-label mode**
21. **Freemium model:** Free (capture + 30-day history) / Pro $2.99/mo (full analytics)

---

## SECURITY: WHAT NEEDS TO BE DONE

### Critical (Before Beta)

#### 1. Complete OWASP Dependency Scan
- **Status:** Plugin installed, NVD API key obtained, scan failed on network
- **Action:** Run scan with API key, review HTML report, address any HIGH/CRITICAL CVEs
- **Command:**
  ```bash
  NVD_KEY=$(grep "owasp.nvd.api.key" /home/poppa/giglens/local.properties | cut -d= -f2)
  cd /home/poppa/giglens && ./gradlew :app:dependencyCheckAnalyze -Powasp.nvd.api.key="$NVD_KEY"
  ```
- **Report location:** `app/build/reports/dependency-check-report.html`

#### 2. Encrypt Room Database with SQLCipher
- **Status:** Not implemented
- **Risk:** Offer data stored in plain SQLite on device
- **Action:** Add SQLCipher dependency, modify `OfferDatabase.kt` to use `SupportFactory`
- **Dependencies to add:**
  ```gradle
  implementation("net.zetetic:android-database-sqlcipher:4.5.4")
  implementation("androidx.sqlite:sqlite-ktx:2.4.0")
  ```

#### 3. Implement EncryptedSharedPreferences
- **Status:** Not implemented (no user auth yet)
- **Risk:** When auth is added, tokens must not be stored in plain SharedPreferences
- **Action:** Add AndroidX Security library, use for all credential storage
- **Dependency:**
  ```gradle
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  ```

#### 4. Enable ProGuard/R8 for Release Builds
- **Status:** `isMinifyEnabled = false` in build.gradle.kts
- **Risk:** Release APK contains unobfuscated code, easy to reverse engineer
- **Action:** Set `isMinifyEnabled = true` for release build type, configure ProGuard rules for Room, ML Kit, and Kotlin
- **File:** `app/build.gradle.kts` release block + `app/proguard-rules.pro`

#### 5. Strip Debug Logging from Release Builds
- **Status:** `Log.d()` and `Log.i()` calls throughout codebase log OCR text, pay amounts, etc.
- **Risk:** Debug logs visible via logcat on release builds
- **Action:** Add ProGuard rule to strip all `Log.d` and `Log.i` calls:
  ```proguard
  -assumenosideeffects class android.util.Log {
      public static int d(...);
      public static int i(...);
      public static int v(...);
  }
  ```

#### 6. Audit Exported Components
- **Status:** `ShareReceiverActivity` is `exported=true` (required for share intent)
- **Risk:** Any app can send intents to ShareReceiverActivity
- **Action:** Validate all incoming intents — check MIME type, handle malformed URIs gracefully, don't crash on bad input. Current code does basic checks but needs hardening.

#### 7. Add .gitignore Entries
- **Status:** `local.properties` added to gitignore
- **Action:** Verify these are all gitignored:
  ```
  local.properties
  *.jks
  *.keystore
  keystore.properties
  app/build/
  .gradle/
  ```

### Important (Before Play Store)

#### 8. Create Privacy Policy
- **What to disclose:**
  - Data collected: offer screenshots (pay, distance, restaurant), timestamps
  - Storage: on-device only, no cloud transmission in current version
  - Permissions: none required for default mode; Accessibility Service optional
  - Data retention: until user deletes
  - No third-party sharing

#### 9. Justify Accessibility Service Usage
- **Google Play requires written justification** for apps using Accessibility Service
- **Our justification:** Automatic offer detection for delivery drivers — reads DoorDash offer screen text to log offer details without manual screenshotting
- **Scoped to:** `com.dd.doordash` only (in `accessibility_service_config.xml`)

#### 10. Sign Release APK with Dedicated Keystore
- **Status:** Using debug keystore
- **Action:** Generate release keystore, store outside repo, document signing process
- **Command:**
  ```bash
  keytool -genkey -v -keystore giglens-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias giglens
  ```
- **NEVER commit the keystore or its password**

#### 11. Network Security Config (When Networking Added)
- **Action:** Create `res/xml/network_security_config.xml`:
  - HTTPS only (no cleartext traffic)
  - Certificate pinning for your API server
  - Reference in manifest: `android:networkSecurityConfig="@xml/network_security_config"`

#### 12. Input Validation Hardening
- **Current:** Pay filtered to $1-$999, restaurant capped at 50 chars
- **Needed:**
  - Sanitize all OCR text before DB insertion (strip null bytes, control characters)
  - Validate screenshot file size (reject suspiciously large files)
  - Rate-limit share processing (prevent rapid-fire intent spam)

### Ongoing (Every Session)

#### 13. Security Check Commands
Run these regularly:
```bash
# Lint — catches security warnings, exported components, insecure configs
cd /home/poppa/giglens && ./gradlew :app:lint

# OWASP — checks dependencies against CVE database
NVD_KEY=$(grep "owasp.nvd.api.key" /home/poppa/giglens/local.properties | cut -d= -f2)
./gradlew :app:dependencyCheckAnalyze -Powasp.nvd.api.key="$NVD_KEY"

# Hardcoded secrets scan
grep -rn "api_key\|password\|secret\|token\|apikey" app/src/ --include="*.kt"

# Insecure network check
grep -rn "http://" app/src/ --include="*.kt" --include="*.xml"

# Exported components audit
grep -n "exported=\"true\"" app/src/main/AndroidManifest.xml
```

#### 14. Dependency Updates
- 12 outdated dependencies flagged by lint (none are security issues currently)
- Review and update quarterly, checking changelogs for security patches
- Never use `+` or `latest` version specifiers

---

## FILE INVENTORY

```
/home/poppa/giglens/
├── CLAUDE.md                          # System prompt (full project context)
├── version.txt                        # Current version (0.1.1)
├── local.properties                   # NVD API key (gitignored)
├── .gitignore
├── build.gradle.kts                   # Top-level: AGP, Kotlin, kapt, OWASP plugins
├── settings.gradle.kts                # Project name, dependency resolution
├── gradle.properties                  # JVM args, AndroidX settings
├── gradle/wrapper/
│   └── gradle-wrapper.properties      # Gradle 8.7
├── app/
│   ├── build.gradle.kts               # App: SDK 34, Room, ML Kit, OWASP config
│   ├── proguard-rules.pro             # Empty placeholder (needs rules before release)
│   └── src/main/
│       ├── AndroidManifest.xml        # Share target, Accessibility, ScreenCapture services
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   ├── values/strings.xml
│       │   ├── values/themes.xml      # Dark theme + transparent theme for share receiver
│       │   └── xml/accessibility_service_config.xml
│       └── java/com/augusteenterprise/giglens/
│           ├── GigLensApp.kt
│           ├── data/
│           │   ├── OfferCapture.kt
│           │   ├── OfferCaptureDao.kt
│           │   └── OfferDatabase.kt
│           ├── ocr/
│           │   └── OfferParser.kt
│           ├── service/
│           │   ├── OfferDetectorService.kt
│           │   ├── OfferNotificationService.kt
│           │   └── ScreenCaptureService.kt
│           └── ui/
│               ├── MainActivity.kt
│               └── ShareReceiverActivity.kt
```

---

## QUICK START FOR NEXT SESSION

```bash
# Verify device connected
adb devices

# Build and install
cd /home/poppa/giglens && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logs while testing
adb logcat -c && adb logcat -s ShareReceiver:* OfferParser:* OfferDetector:* ScreenCapture:*

# Check for crashes
adb logcat -d | grep "FATAL EXCEPTION" | tail -5

# Run security lint
./gradlew :app:lint

# Git status
cd /home/poppa/giglens && git status && cat version.txt
```
