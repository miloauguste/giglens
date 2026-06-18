# GigLens — Session Handover
**Date:** 2026-06-18
**Version at session end:** 0.1.172 (multiple deploys this session via deploy.sh auto-commit)
**Build state:** PASSING (compileDebugKotlin + compileReleaseKotlin both clean, push clean)
**Conducted by:** Claude (Anthropic) — manual session, paired with Milo via live ADB/logcat debugging

---

## What Was Completed This Session

### 1. Built and wired debug offer-capture email pipeline (DebugOfferEmailer.kt)
- New file: `app/src/main/java/com/augusteenterprise/giglens/debug/DebugOfferEmailer.kt`
- Sends a debug email per detected offer: screenshot PNG attachment + all raw
  extracted fields (pay, distance, restaurant, deliverBy, source) + full
  ScoreResult pill values (score, verdict, netValue, payPerMile,
  truePayPerMile) + town estimate (displayName, confidence)
- Gated by BuildConfig.DEBUG — hard no-op compile-time exclusion in release
- Screenshot matched by mtime proximity (within 15s of payload timestamp),
  since screenshot and broadcast are produced by two different async paths
- Call site: inside `if (result != null)` block in
  AccessibilityOfferReceiver.kt, right after OfferScorer.score() returns —
  only place where extracted data AND pill result are simultaneously in scope
- SMTP: Gmail (smtp.gmail.com:587/STARTTLS), credentials via
  GIGLENS_DEBUG_SMTP_USER / GIGLENS_DEBUG_SMTP_PASS environment variables
  (same pattern as GIGLENS_STORE_PASSWORD for the keystore)
- Future-proofing: explicit TODO(opt-in-version) comment marking where this
  becomes a per-driver opt-in "help improve GigLens" feature — current
  version is single hardcoded recipient, debug-only, no consent flow

### 2. Fixed JavaMail dependency issues
- Added com.sun.mail:android-mail:1.6.7 and android-activation:1.6.7 to
  build.gradle.kts
- Fixed FileDataSource unresolved (not available in the Android JavaMail fork)
  — switched to ByteArrayDataSource with screenshotFile.readBytes()
- Fixed duplicate META-INF/NOTICE.md packaging conflict between both JARs —
  added packaging { resources { excludes += [...] } } block (AGP 8.2.2 DSL)
- Fixed DEBUG_SMTP_USER/PASS unresolved in release variant — moved
  buildConfigField entries from debug{} to defaultConfig{} so they exist in
  every variant's BuildConfig

### 3. Fixed SecurityException crash in testTakeScreenshot()
- Crash: java.lang.SecurityException: Services don't have the capability of
  taking the screenshot — confirmed via full FATAL EXCEPTION stack trace
  pulled from live logcat during real-device test
- Root cause: android:canTakeScreenshot="true" was MISSING from
  accessibility_service_config.xml — required for takeScreenshot() at the
  OS level regardless of other permissions
- Fix: added the attribute to the <accessibility-service> XML element
- CRITICAL MANUAL STEP: capability changes require manually toggling the
  accessibility service OFF then ON in Settings → Accessibility after
  reinstalling the updated APK — a code/XML change alone is insufficient
  since Android caches capability set at enable time, not app-restart time
- This corrects an incorrect claim from the previous session's handover which
  stated "requires no additional permission beyond what's already granted"

### 4. Fixed stale overlay pill state bug in OfferOverlayService
- Symptom: pill sometimes shows stale offer data (old pay/restaurant/verdict)
  on service restart, or appears on non-offer screens inconsistently
- Root cause traced: sheetState is a service-instance field initialized to
  IDLE only at construction time. showWidget() draws a hardcoded gray idle
  pill (does NOT read sheetState). State-aware rendering only happens in
  updateWidget(). If the overlay window is lost (IllegalArgumentException
  catch path, e.g. after OfferDetectorService crash-restart) while
  sheetState still holds a past PILL/CAMERA value, and a stray
  SHOW_CAMERA/HIDE_CAMERA broadcast then arrives, the else {showWidget();
  updateWidget()} branch re-renders against the stale state
- Fix: reset sheetState = SheetState.IDLE at both silent re-attach paths
  in onStartCommand (IllegalArgumentException catch AND plain !isViewAdded)
- NOT YET VALIDATED on a real shift — compile-verified only

### 5. Changed AUTO_CAPTURE_MODE default to "accessibility" (fully automatic)
- Milo's explicit preference for dev/testing workflow
- Found and reconciled three drifted fallback defaults:
  AppConfig.kt seed: "off" → changed to "accessibility"
  MainActivity.kt null-fallback: "accessibility" (already correct)
  OfferDetectorService.kt null-fallback: "tap" → changed to "accessibility"
- Added explicit pre-launch review flag in FEATURE_BACKLOG.md — this is
  a dev-convenience change that may not be the right default for real drivers
  (interacts with the disclosure screen content, Play Store review scrutiny
  of default-on automatic capture). Must be explicitly decided before public
  release, not inherited silently.

### 6. Live-validated the full accessibility detection pipeline via ADB logcat
- Connected Pixel 10 XL via wireless ADB (port 42095 that session)
- Confirmed real DoorDash offer triggered looksLikeOfferScreen() correctly:
  accept=true decline=true dollar=true guaranteed=true mi=true → isOffer=true
  pay=6.15 dist=3.2 restaurant=McDonald's deliverBy=10:42 PM countdown=40
- Confirmed the SecurityException crash for the first time (testTakeScreenshot
  had never been run on a real offer before this session)
- Full email pipeline NOT yet validated end-to-end on real device

### 7. Credential exposure incident — fully remediated
- WHAT HAPPENED: google-play-api-key.json (GCP service account JSON key for
  giglens-deploy@gen-lang-client-0130402808.iam.gserviceaccount.com) was
  committed to git on 2026-06-09, before any .gitignore rule existed for it.
  The file sat in untracked/unpushed commits for 9 days until GitHub's push
  protection caught it on this session's first push attempt.
- TIMELINE: file committed June 9 → session's first git push attempt blocked
  by GitHub push protection → old key revoked in Google Cloud Console →
  service account accidentally deleted entirely → service account recreated
  (giglens-deploy, same name, same project) → new JSON key generated →
  Play Console API access re-granted (Release apps to testing tracks,
  scoped to GigLens only, no account-wide permissions) → deploy.sh tested
  end-to-end → "Successfully finished the upload to Google Play" confirmed →
  git filter-repo used to strip the file from all historical commits →
  .gitignore rule added → git push clean on retry
- ACTUAL SECURITY RISK: minimal. Old key never reached a public remote
  (GitHub push protection blocked every push attempt). Old key/account now
  deleted and cannot be used by anyone.
- ROOT CAUSE: .gitignore had no rule for google-play-api-key.json, and
  Gitleaks pre-commit hook was using --no-git (working-tree scan) instead
  of --staged (staged-diff scan), and had no explicit rule matching GCP
  service-account JSON structure
- ALL FIXES APPLIED AND VERIFIED:
  * .gitignore now has explicit rule for google-play-api-key.json
  * .gitleaks.toml now has explicit rules: gcp-service-account-json and
    gcp-private-key-block
  * pre-commit hook changed from gitleaks detect --no-git to
    gitleaks protect --staged (scans exactly what is being committed)
  * Verified via synthetic fake-credential test: leaks found: 2, exit
    code 1 — confirmed working before committing the fix
  * git filter-repo stripped google-play-api-key.json from all historical
    commits — file has zero presence in git history (confirmed via
    git log --all -- google-play-api-key.json returning empty)
  * New service account permissions correctly scoped: Release apps to
    testing tracks only, GigLens app only, no admin/financial/production
    release access

### 8. Fixed NVD/OWASP Dependency Check pipeline issues
- Found that -Powasp.nvd.api.key=... on the Gradle command line was silently
  ignored — no code anywhere in build.gradle.kts was reading this property
  and wiring it into the plugin's nvd { apiKey = ... } block, despite this
  being the intended mechanism for years
- Fixed: added nvd { val key = project.findProperty("owasp.nvd.api.key") as
  String? ?: System.getenv("NVD_API_KEY") ?: ""; if (key.isNotBlank()) {
  apiKey = key } } block inside dependencyCheck {} in build.gradle.kts
- The NvdCveClient NPE (java.lang.NullPointerException: Cannot read the
  array length because "bytes" is null) is a confirmed known upstream bug
  in the openvulnerability-cli library, reported across plugin versions 10.0.4
  through 12.1.1+ in multiple open GitHub issues as of this writing. Not
  fixable locally.
- Pre-push hook: OWASP downgraded from hard block to advisory warning, with
  improved error output (saves full log to /tmp/giglens_owasp_*.log, detects
  the specific NPE pattern and labels it as "known upstream bug" vs unknown
  failure) — no longer silently discarding stderr with 2>/dev/null
- Local OWASP scan then disabled entirely from pre-push hook (slow, unreliable
  due to upstream bug) — replaced by GitHub Dependabot

### 9. Set up GitHub Dependabot for Gradle dependency scanning
- Added .github/dependabot.yml — weekly Gradle dependency scan, up to 10
  open PRs at once
- Dependabot enabled via GitHub repo settings (Security Analysis page)
- Free, GitHub-native, server-side — no local runtime cost, no NVD client
  bugs, no API key maintenance required
- When an alert fires: check severity + whether the vulnerable code path is
  reachable in GigLens → merge Dependabot's auto-PR if it opens one →
  manually bump build.gradle.kts if no PR is generated → add suppression
  with documented justification only as last resort. Critical/high in
  reachable code = fix promptly. Low/moderate in unreachable path = can wait.

### 10. Fixed Gitleaks pre-commit hook — two distinct gaps closed
- Gap 1: gitleaks detect --no-git scanned the working tree, not the staged
  diff — changed to gitleaks protect --staged (scans exactly what's about
  to be committed). This is the structural fix that would have caught the
  June 9th credential commit.
- Gap 2: .gitleaks.toml had no rules for GCP service-account JSON or PEM
  private key blocks — added explicit rules: gcp-service-account-json and
  gcp-private-key-block. Verified via synthetic test file before committing.

### 11. Added nightly NVD database update cron job
- 3am daily: ./gradlew app:dependencyCheckUpdate -Powasp.nvd.api.key=...
  → /home/poppa/logs/giglens_dependency_check_update.YYYY-MM-DD.log
- Purpose: keep the local NVD database warm so that if OWASP local scanning
  is ever re-enabled, the first run after a long gap doesn't trigger a full
  multi-hour database rebuild from scratch
- NVD api key wired correctly via -P now that the build.gradle.kts fix (#8)
  is in place

### 12. Fixed commit history and push hygiene gaps
- Committed 33+ previously-unpushed commits (some going back to June 9th,
  including DeliveryTownEstimator.kt and TownAccuracyReceiver.kt which were
  never committed despite being marked "complete" in prior handovers)
- Added .gitignore rules for session scratch artifacts: *.backup.*,
  *.broken_today, root-level patch_*.py scripts, docs/*.log, docs/*.db
- Documented that deploy.sh auto-commits on every run (git commit --allow-empty
  -m "release: $CHANGELOG") — not all session changes go through manual git
  commit; some are already captured incrementally by deploy.sh
- All commits now pushed to origin/main (confirmed: git log origin/main..HEAD
  returns empty after final push)
- CHANGELOG.md backfilled with consolidated entry for v0.1.80 through v0.1.172
  (had not been updated since v0.1.79 on 2026-06-02)

---

## What Was Left Incomplete

- testTakeScreenshot() capability fix applied and compile-verified but NOT
  yet confirmed working on a real device — requires manual accessibility
  service re-toggle (off then on in Settings) after reinstalling the APK, and
  a real shift to trigger a real offer. This is the first thing to confirm
  next session.
- Debug email pipeline (DebugOfferEmailer) compile-verified but ZERO real
  offers have been processed through it. Specifically unconfirmed: whether
  Gmail SMTP auth actually succeeds with the App Password, whether the email
  arrives, whether the screenshot attachment is present and matches the right
  offer, whether verdict field produces a clean human-readable string (e.g.
  "GREEN" vs raw enum toString like "Verdict@hashcode"). Confirm via logcat
  on the next shift before trusting email content.
- Stale overlay pill fix compile-verified but not validated on real device —
  watch for gray/idle pill on first enable rather than stale offer data.
- AUTO_CAPTURE_MODE default change not visually confirmed (app was
  reinstalled/fresh during this session) — confirm Settings shows "Fully
  Automatic" pre-selected on next launch.
- git filter-repo used to strip google-play-api-key.json from history — this
  rewrote commit hashes for every commit from June 9th onward. If any other
  tool or integration was using these commit hashes as anchors (unlikely for
  a solo project, but worth knowing) those references are now stale.
- Old key/account deletion was accidental (entire service account deleted
  rather than just the key) — intentional rotation would have been cleaner
  (delete key → create new key, no service account recreation needed). No
  practical consequence since the result is the same, but worth remembering
  for future key rotations: delete the KEY only, not the service account.
- Accessibility disclosure screen still not built (ViewBinding) — now has
  an additional open question about default-capture-mode language given the
  AUTO_CAPTURE_MODE default change this session.
- Phase 1 scoring redesign (GREEN/YELLOW/RED thresholds), Registration +
  Stripe billing, Sentiment Agent repo — all untouched this session.

---

## Known Broken (do not ignore)

- Location permission reset to denied after the app was uninstalled/reinstalled
  during this session — must manually re-grant before any town-estimation
  testing (expected Android behavior, not a new bug)
- Delivery town estimation still fundamentally broken (GPS bearing unreliable
  at low speed) regardless of the permission fix — this is a known architecture
  limitation, not something the permission grant alone will resolve
- testTakeScreenshot() still not confirmed working — the SecurityException
  crash has been fixed in code, but the manual accessibility service re-toggle
  (required after a capability XML change) has not yet been performed on the
  current install. DO NOT assume it works until confirmed via logcat showing
  "testTakeScreenshot SUCCESS" on a real offer screen.
- Pre-existing compiler warnings (not regressions from this session):
  AccessibilityOfferReceiver.kt:136 "Condition distance != null is always true"
  AccessibilityOfferReceiver.kt:224 "Elvis operator always returns left operand"
  OfferDetectorService.kt:133 "Variable mode is never used"
  OfferDetectorService.kt:212,234,267,406,459 "recycle(): Unit is deprecated"
  OfferOverlayService.kt:434 "Name shadowed: params"
  None of these block the build.

---

## Next Session — Start Here

1. CRITICAL FIRST STEP: manually toggle accessibility service OFF then ON in
   Settings → Accessibility → GigLens on the Pixel before any testing.
   This is required because android:canTakeScreenshot="true" was added to
   the XML config this session — without the re-toggle, Android still uses
   the old cached capability set and testTakeScreenshot() will crash with
   the same SecurityException as before the fix.

2. Confirm DEBUG_RECIPIENT in DebugOfferEmailer.kt is set to Milo's real
   email address (not the you@example.com placeholder).

3. Confirm GIGLENS_DEBUG_SMTP_USER / GIGLENS_DEBUG_SMTP_PASS are exported
   in the shell BEFORE any rebuild:
   export GIGLENS_DEBUG_SMTP_USER="youraddress@gmail.com"
   export GIGLENS_DEBUG_SMTP_PASS="your16charapppassword"
   These get baked into BuildConfig at Gradle build time — empty values at
   build time = emails silently skipped with a log warning, not a crash.

4. Re-grant location permission on the Pixel:
   Settings → Apps → GigLens → Permissions → Location → Allow
   (wiped during uninstall/reinstall this session)

5. Connect Pixel via wireless ADB:
   cat ~/giglens/.pixel_port
   adb connect 10.0.0.110:$(cat ~/giglens/.pixel_port)
   adb devices

6. Start filtered logcat covering all components touched this session:
   adb -s 10.0.0.110:$(cat ~/giglens/.pixel_port) logcat | grep -E
   "OfferDetector|AccessibilityOfferReceiver|DebugOfferEmailer|testTakeScreenshot|OfferOverlayService"

7. On next real shift or search-and-log-off validation, watch for IN ORDER:
   - looksLikeOfferScreen: ... → isOffer=true
   - testTakeScreenshot() called → then "SUCCESS" (not SecurityException)
   - ACTION_OFFER_EXTRACTED received
   - Score: ... | Verdict: ... | Net: ...
   - DebugOfferEmailer: Debug offer email sent (or "failed: ..." if SMTP issue)
   Then check Gmail inbox for the actual email with screenshot attachment.
   Check verdict field specifically — should be a clean string like "GREEN",
   not a Java object reference.

8. Check overlay pill behavior:
   - Enable widget toggle in Settings → pill should appear gray/idle
   - NOT stale offer data from a previous offer
   - Confirm it stays idle until a real offer triggers SHOW_CAMERA

9. Check Settings → Capture Mode → "Fully Automatic" should be pre-selected
   on fresh install without any manual toggle needed.

10. Check Dependabot alerts tab:
    https://github.com/miloauguste/giglens/security/dependabot
    Any alerts that appeared since enabling? If so, review and handle per
    the protocol documented in this handover (severity → check reachability
    → merge PR or bump manually).

11. Once steps 1-9 are all confirmed working end-to-end: proceed with
    previously-scoped work in priority order:
    - Decide pill display (town vs profit-only) — still undecided since last
      session, now gated on screenshot/email validation from above
    - Decide AUTO_CAPTURE_MODE default for real drivers (see FEATURE_BACKLOG.md)
    - Accessibility disclosure screen rebuild (ViewBinding)
    - OpenCV pin-detection pipeline (blocked on screenshot validation)
    - Phase 1 scoring redesign (GREEN/YELLOW/RED)
    - Registration + Stripe billing
    - Sentiment Agent repo

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated live via logcat this session |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | Validated live — pay=6.15 dist=3.2 restaurant=McDonald's confirmed |
| Offer dedup guard (receiver-level) | ✅ Working | Unaffected this session |
| Delivery town estimation (GPS bearing) | ❌ Broken | Unchanged — bearing unreliable at low speed, permission also needs re-grant |
| testTakeScreenshot() | 🔲 Fix applied, NOT validated | canTakeScreenshot XML flag added, SecurityException root-caused — needs re-toggle + real offer test |
| Debug offer-capture email pipeline | 🔲 Built, NOT validated | Compiles clean, logic traced — needs real shift to confirm end-to-end |
| Town accuracy tracking infrastructure | ✅ Built (dormant) | Unaffected this session |
| Overlay pill stale-state fix | 🔲 Fix applied, NOT validated | sheetState reset added — needs real device observation |
| AUTO_CAPTURE_MODE default | 🔲 Changed, NOT visually confirmed | Seed default changed to "accessibility" — needs fresh install observation |
| Google Play deploy pipeline | ✅ Working | New service account + key + Play Console grant verified via successful upload |
| Gitleaks staged-diff scanning | ✅ Working | Verified via synthetic credential test — leaks found: 2, exit code 1 |
| GitHub Dependabot | ✅ Enabled | .github/dependabot.yml committed, weekly Gradle scan active |
| Local OWASP Dependency Check | ⚠️ Disabled | Replaced by Dependabot — removed from pre-push hook entirely |
| Accessibility disclosure screen | 🔲 Not started | Unchanged — now has additional open question about default-mode language |
| Scoring redesign (Phase 1) | 🔲 Scoped | Untouched |
| Registration + Stripe billing | 🔲 Scoped | Untouched |
| Sentiment Agent repo | 🔲 Scoped | Untouched |

---

## Decisions Made This Session

1. Debug email pipeline built as a separate, future-extensible class
   (OfferDebugPayload + DebugOfferEmailer) rather than inline code —
   specifically because Milo stated intent to turn this into per-driver
   opt-in cross-driver analytics eventually. Current version is NOT
   consent-gated and must not be extended into that feature without adding
   a destination + consent flow first.

2. Gmail chosen over GoDaddy SMTP relay for debug email — Milo's preference,
   simpler for this debug-only use case. Requires a Gmail App Password (16
   chars), not the real account password.

3. buildConfigField SMTP credentials moved to defaultConfig{} not debug{} —
   so they exist in every variant's BuildConfig including release, since
   deploy.sh builds the release variant. Matches GIGLENS_STORE_PASSWORD
   pattern (env-var-based, applies across all variants).

4. android:canTakeScreenshot="true" confirmed required for takeScreenshot() —
   corrects prior session's incorrect claim. Manual re-toggle in Android
   Settings required after any change to this XML attribute — Android caches
   capability set at enable time, not app restart time. Explicitly decided
   NOT to build automatic forced re-toggle logic (disrupts drivers on every
   app restart for a one-time dev migration problem).

5. AUTO_CAPTURE_MODE default changed to "accessibility" for Milo's dev/testing
   convenience. Explicitly flagged as NOT DECIDED for real drivers — must be
   revisited before Play Store submission, tied to disclosure screen content.

6. OWASP Dependency Check local scan removed from push pipeline entirely —
   replaced by GitHub Dependabot. Rationale: upstream NvdCveClient NPE bug
   confirmed unfixed across plugin versions 10.0.4 through 12.1.1+ as of
   writing; Dependabot provides equivalent coverage with zero local runtime
   cost and no dependency on NVD's API reliability.

7. Gitleaks: gitleaks protect --staged chosen over gitleaks detect --no-git
   for the pre-commit credential scan. --staged scans exactly what is being
   committed; --no-git scans the full working tree which is the wrong scope
   for a pre-commit hook (confirmed as root cause of June 9th credential
   slip-through).

8. Service account permissions scoped to "Release apps to testing tracks" +
   GigLens app only — not account-wide admin, not production release access.
   Matches least-privilege principle. If deploy.sh ever fails with a
   permission error, check this scope first before broadening.

9. git filter-repo chosen over GitHub's "allow this secret" bypass for the
   push-protection block — cleaner long-term outcome (secret genuinely gone
   from history) vs. just marking it as allowed (secret stays in history,
   just whitelisted). Since no commits had been successfully pushed before
   this was caught, history rewrite was safe (no one else had pulled these
   commit hashes).

10. Automated testing discussion (Espresso, Robolectric, UI Automator, ATF):
    confirmed these are the right tools for their respective use cases but
    remain in backlog (T1 tabled per FEATURE_BACKLOG.md — too early while app
    is in active development). Robolectric for DB migration testing and
    Espresso for disclosure screen validation are the highest-value, lowest-
    effort additions when the time comes.

---

## Security Changes This Session (summary)

- google-play-api-key.json: stripped from git history, gitignored, new key
  deployed and verified
- .gitleaks.toml: added gcp-service-account-json and gcp-private-key-block
  rules, verified working
- pre-commit hook: gitleaks now uses protect --staged, not detect --no-git
- pre-push hook: OWASP disabled (replaced by Dependabot), Gitleaks improved
- .github/dependabot.yml: added, weekly Gradle CVE scanning active
- google-play-api-key.json .gitignore rule: confirmed active
  (git check-ignore -v returns match at .gitignore:49)

---

## Environment Changes This Session

- New env vars required at Gradle build time:
  GIGLENS_DEBUG_SMTP_USER (Gmail address for debug offer emails)
  GIGLENS_DEBUG_SMTP_PASS (Gmail App Password — 16 chars, NOT account password)
  Add to ~/.bashrc alongside MOBSF_APIKEY, GIGLENS_STORE_PASSWORD, etc.
- New cron job: 0 3 * * * cd ~/giglens && ./gradlew app:dependencyCheckUpdate
  -Powasp.nvd.api.key=A1B7BC3C-1350-F111-836C-0EBF96DE670D --quiet >>
  /home/poppa/logs/giglens_dependency_check_update.YYYY-MM-DD.log 2>&1
- google-play-api-key.json: NEW file at same path — different service account
  key than what existed before this session. Fastlane/deploy.sh still works
  (confirmed via successful upload to Play Store during this session).
- Pixel 10 XL: app was uninstalled and fresh-installed during this session —
  all permissions, accessibility grants, and local DB data wiped.
- Wireless ADB port: changes on every reboot — always check
  ~/giglens/.pixel_port or Wireless Debugging settings, never assume a port
  from a previous session.

---

## Files Changed This Session

Core source:
- app/src/main/java/com/augusteenterprise/giglens/debug/DebugOfferEmailer.kt (NEW)
- app/src/main/java/com/augusteenterprise/giglens/service/AccessibilityOfferReceiver.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt
- app/src/main/java/com/augusteenterprise/giglens/data/AppConfig.kt
- app/src/main/res/xml/accessibility_service_config.xml
- app/build.gradle.kts (multiple changes: JavaMail deps, packaging excludes,
  defaultConfig SMTP fields, nvd{apiKey} block)

Security/CI:
- .gitignore (google-play-api-key.json rule added)
- .gitleaks.toml (gcp-service-account-json + gcp-private-key-block rules)
- .git/hooks/pre-commit (gitleaks protect --staged, NOT tracked in git)
- .git/hooks/pre-push (OWASP disabled, OWASP warn logic improved, NOT tracked)
- .github/dependabot.yml (NEW — Dependabot config)

Documentation:
- docs/LAST_SESSION.md (this file)
- docs/LAST_SESSION_20260618.md (dated permanent copy)
- docs/FEATURE_BACKLOG.md (AUTO_CAPTURE_MODE pre-launch review flag)
- CHANGELOG.md (backfilled v0.1.80 through v0.1.172)

---

## Features Left to Implement (Priority Ranked)

See docs/FEATURE_BACKLOG.md for full list. Top of stack:

1. Validate everything built this session on next real shift — this single
   item gates nearly everything else (see Next Session steps 1-9 above)
2. Decide pill display (town vs profit-only) — blocked on #1
3. Decide AUTO_CAPTURE_MODE default for real drivers — must resolve before
   Play Store submission
4. Accessibility disclosure screen rebuild (ViewBinding) — now with additional
   open question about default-mode language
5. OpenCV pin-detection pipeline — blocked on screenshot validation from #1
6. Phase 1 scoring redesign (GREEN/YELLOW/RED) — untouched
7. Registration + Stripe billing — untouched
8. Sentiment Agent repo — untouched

---

*Next developer: read SESSION_PROTOCOL.md and WORKING_AGREEMENTS.md first,
then return here. PRIORITY NOTE: this session built/fixed a LOT of things
that are compile-verified but zero-percent runtime-validated (testTakeScreenshot,
debug email pipeline, overlay stale-state fix, AUTO_CAPTURE_MODE default).
The very first thing next session MUST be a real live test of these —
not a code review, not more feature work. Per WORKING_AGREEMENTS.md: verify,
do not assume. The manual accessibility service re-toggle is specifically
easy to forget and specifically required — it is called out in step 1 of
Next Session for exactly that reason.*
