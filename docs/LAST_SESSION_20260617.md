# GigLens — Session Handover
**Date:** 2026-06-17
**Version at session end:** 0.1.165 (multiple deploys this session — see Files Changed)
**Build state:** PASSING (compileDebugKotlin + compileReleaseKotlin both clean)
**Conducted by:** Claude (Anthropic) — manual session, paired with Milo via logcat-driven live debugging

---

## What Was Completed This Session

### 1. Built debug-only offer-capture email pipeline (screenshot + extracted data + pill result)
- New file: `app/src/main/java/com/augusteenterprise/giglens/debug/DebugOfferEmailer.kt`
- Purpose: on every detected offer, email a debug capture (screenshot PNG + raw
  extracted fields + computed ScoreResult + town estimate) to a fixed personal
  address, for manual validation of town accuracy, pin detection groundwork,
  and scoring — without needing to inspect the device directly after each shift
- Gated by `BuildConfig.DEBUG` — hard no-op in release builds, never ships to
  Play Store testers
- `OfferDebugPayload` data class carries: timestamp, payAmount, distance,
  restaurant, deliverByRawText, source, score, verdict, netValue, payPerMile,
  truePayPerMile, townDisplayName, townConfidence
- Screenshot matching: searches `debug/` folder for the most recent
  `offer_screenshot_*.png` within 15s of the payload timestamp (screenshot and
  broadcast are produced by two different async call sites, so exact
  same-instant correlation isn't guaranteed — mtime-proximity matching used
  instead)
- Wired into `AccessibilityOfferReceiver.kt` — call site is inside the
  `if (result != null)` block, immediately after `OfferScorer.score()` returns,
  where extracted data + pill result are both in scope simultaneously
- Future-proofing: explicit `TODO(opt-in-version)` comment marks where this
  becomes a per-driver opt-in "help improve GigLens" feature later (Milo's
  stated intent — eventually have other drivers share this same data for
  cross-driver analytics). Current version is intentionally NOT consent-gated
  or per-user — single hardcoded recipient, debug-only. Do not extend this
  class directly into the opt-in feature without adding a destination +
  consent flag first.

### 2. Fixed JavaMail dependency compile error — FileDataSource unresolved
- Root cause: `com.sun.mail:android-mail` / `android-activation` (the
  Android-compatible JavaMail forks) do NOT include `javax.activation.FileDataSource`
  — that class exists in desktop JavaMail/Activation Framework but was trimmed
  from the Android port
- Fix: switched to `javax.mail.util.ByteArrayDataSource`, reading the
  screenshot file into a byte array first (`screenshotFile.readBytes()`)
  instead of passing the `File` directly
- Confirmed via actual `compileDebugKotlin` run — first attempt with
  `FileDataSource` failed with "Unresolved reference", second attempt with
  `ByteArrayDataSource` succeeded

### 3. Fixed release-variant build failure — DEBUG_SMTP_USER/PASS unresolved in release
- Root cause: `buildConfigField` calls for `DEBUG_SMTP_USER`/`DEBUG_SMTP_PASS`
  were originally added inside the `debug { }` build type block only. `deploy.sh`
  invokes `compileReleaseKotlin` (release variant signed with release keystore
  for sideload-without-conflict, per existing project convention) — release's
  generated `BuildConfig` never had these fields, since `debug{}` and
  `release{}` produce separate `BuildConfig` classes that don't inherit each
  other's `buildConfigField` calls
- Fix: migrated both fields from `debug { }` to `defaultConfig { }`, so they
  exist in every variant's BuildConfig — matches the project's existing pattern
  for `GIGLENS_STORE_PASSWORD`/`GIGLENS_KEY_PASSWORD` (read in `signingConfigs`,
  which also applies across all variants)
- Verified via actual `compileDebugKotlin` + `compileReleaseKotlin` run, both
  clean

### 4. Switched SMTP relay from GoDaddy to Gmail
- Milo's preference — simpler than provisioning through the existing GoDaddy
  relay for this debug-only use case
- Changed `SMTP_HOST` in `DebugOfferEmailer.kt` from `smtpout.secureserver.net`
  to `smtp.gmail.com` (port 587/STARTTLS unchanged)
- Requires a Gmail **App Password** (16-char), NOT the real account password —
  Gmail rejects plain SMTP auth if 2FA is enabled (assumed to be the case)
- Same `GIGLENS_DEBUG_SMTP_USER`/`GIGLENS_DEBUG_SMTP_PASS` env var names kept,
  just pointed at Gmail credentials instead of GoDaddy

### 5. Fixed packaging conflict — duplicate META-INF files from JavaMail deps
- `mergeReleaseJavaResource` failed: `2 files found with path 'META-INF/NOTICE.md'`
  — both `android-mail` and `android-activation` JARs ship identical
  license/notice files at the same path
- Fix: added a `packaging { resources { excludes += [...] } }` block (AGP 8.2.2
  modern DSL, not the deprecated `packagingOptions` alias) excluding
  `META-INF/NOTICE.md`, `LICENSE.md`, `LICENSE.txt`, `NOTICE.txt` proactively,
  since this class of duplicate-license-file collision across two JARs from
  the same vendor commonly cascades to the next file once the first is
  excluded
- Verified via successful `./deploy.sh` build after this fix

### 6. ROOT CAUSE FOUND + FIXED: testTakeScreenshot() crash — SecurityException
- First real validation of `testTakeScreenshot()` (built last session, never
  tested) produced: `java.lang.SecurityException: Services don't have the
  capability of taking the screenshot.` — confirmed via full FATAL EXCEPTION
  stack trace pulled from `adb logcat -d`
- Root cause: `AccessibilityService.takeScreenshot()` requires the service to
  explicitly declare `android:canTakeScreenshot="true"` in its accessibility
  service XML config. This was MISSING from
  `app/src/main/res/xml/accessibility_service_config.xml`. Without this flag,
  Android's `IAccessibilityServiceConnection` rejects the call at the system
  level before any app code runs.
- **This corrects an incorrect claim from the PREVIOUS session's handover**,
  which stated takeScreenshot() "requires no additional permission beyond
  what's already granted during onboarding" — that was wrong/incomplete, only
  caught by actually running it. Per WORKING_AGREEMENTS.md's core principle
  (verify, don't assume), this is exactly the failure mode that doc warns
  about — should be referenced if a similar "I'm confident this just works"
  claim comes up again before testing.
- Fix applied: added `android:canTakeScreenshot="true"` to the
  `<accessibility-service>` element in the XML config
- **CRITICAL — NOT JUST AN XML EDIT**: the capability change does not take
  effect on the already-running/already-granted accessibility service
  connection. Android caches the declared capability set at the time the
  service was last manually enabled via Settings. A code/manifest change alone
  is insufficient — the accessibility service must be manually toggled OFF
  then ON again in Settings → Accessibility after reinstalling the updated
  APK, or the SAME SecurityException will recur even with the XML fixed. This
  was initially missed (rebuild without re-toggle), confirmed not to fix it,
  then the re-toggle requirement was identified as the actual missing step.
- Status: XML fix applied, compiles clean. NOT YET CONFIRMED on a real
  takeScreenshot() success — app was uninstalled/reinstalled later this
  session for an unrelated reason (see #7), so the toggle needs to happen
  fresh on the next live test regardless.

### 7. Fixed signature mismatch on sideload — INSTALL_FAILED_UPDATE_INCOMPATIBLE
- `deploy.sh` sideload failed: "Existing package... signatures do not match
  newer version" — the Pixel had an APK installed signed with a different
  keystore than the current build produces (likely a leftover debug-keystore
  build from before the project's "sign debug with release keystore" decision
  was made in an earlier session)
- Fix: `adb uninstall com.augusteenterprise.giglens`, then re-ran `./deploy.sh`
  — succeeded
- **Side effect, expected and accepted**: uninstall wiped all local app data —
  offer history DB, accessibility service grant, location permission grant,
  GPS settings toggle. All needed manual re-granting on next launch (separate
  from the takeScreenshot capability re-toggle in #6, though both needed doing
  on the same fresh install).

### 8. Changed AUTO_CAPTURE_MODE default to "accessibility" (fully automatic)
- Milo's explicit preference: Settings → Capture Mode should default to Fully
  Automatic (the no-tap accessibility-based pipeline), not "Off"
- Found and reconciled THREE different fallback defaults for the same config
  key that had drifted out of sync across the codebase:
  - `AppConfig.kt` seed data: `"off"` (this is what actually populates the DB
    row on a fresh install — the one that mattered for testing today, since
    the app had just been uninstalled per #7)
  - `MainActivity.kt` null-fallback: `"accessibility"` (already correct, no
    change needed)
  - `OfferDetectorService.kt` null-fallback: `"tap"` (inconsistent with the
    other two — changed to match)
- Changed `AppConfig.kt` seed default from `"off"` → `"accessibility"`
- Changed `OfferDetectorService.kt` null-fallback from `"tap"` →
  `"accessibility"` for consistency
- Verified via compileDebugKotlin + compileReleaseKotlin, both clean
- **Added explicit pre-launch review flag in FEATURE_BACKLOG.md** — this
  default change was made for Milo's personal dev/testing convenience.
  Whether a FRESH INSTALL FOR A REAL DRIVER should default to fully-automatic
  vs. off is a SEPARATE decision that interacts directly with the
  still-unbuilt Accessibility Disclosure Screen (a default-on automatic
  capture mode is a stronger behavioral claim than opt-in, and Google Play's
  accessibility-service review scrutinizes default-on capture more heavily).
  Explicitly flagged as NOT YET DECIDED, must be revisited before Play Store
  submission — see FEATURE_BACKLOG.md new section for full reasoning and open
  questions.

### 9. Diagnosed and fixed overlay pill showing stale/incorrect state
- Milo reported: pill sometimes appears before DoorDash is even running, and
  inconsistently appears to "leak" — initially described as possibly
  appearing on other apps' screens too, though Milo later clarified this part
  was not consistent/reliably reproduced
- Traced root cause through `OfferOverlayService.kt`: `sheetState` is a
  service-instance field that initializes to `IDLE` only ONCE at construction
  time — it is NOT reset on every `onStartCommand`. `showWidget()` itself only
  ever draws a neutral gray idle pill (hardcoded color, doesn't read
  `sheetState`). The STATE-aware rendering only happens in `updateWidget()`.
- Actual failure path identified: if the overlay WINDOW is lost (caught via
  `IllegalArgumentException` in `onStartCommand`, e.g. from a service process
  restart — confirmed earlier this session that `OfferDetectorService` DOES
  crash-and-restart under certain conditions, see #6) while `sheetState` in
  memory still holds a stale value from a PAST real offer (e.g. `PILL`), and
  then a stray `SHOW_CAMERA`/`HIDE_CAMERA` broadcast arrives afterward, the
  `else { showWidget(); updateWidget() }` branch in those action handlers
  fires `updateWidget()` against the STALE `sheetState` — rendering old
  offer data (pay/restaurant/verdict) on a freshly re-attached window,
  potentially while a different app is in foreground
- Fix applied: in `onStartCommand`'s two silent re-attach paths (the
  `IllegalArgumentException` catch, and the plain `!isViewAdded` branch),
  explicitly reset `sheetState = SheetState.IDLE` before calling
  `showWidget()` — ensures a freshly re-attached window never inherits stale
  in-memory offer state
- Verified via compileDebugKotlin + compileReleaseKotlin, both clean
- **NOT YET VALIDATED on a real device** — Milo deferred live testing of this
  fix (along with everything else this session) to the next actual shift

---

## What Was Left Incomplete

- **testTakeScreenshot() still not confirmed working on a real offer.** XML
  capability fix applied, but the required manual accessibility-service
  re-toggle (off then on in Settings) has NOT yet been done on the current
  install — app was uninstalled/reinstalled for an unrelated signature-mismatch
  reason AFTER the XML fix, so this is a fresh requirement on next launch, not
  a leftover from before.
- **Debug email pipeline (DebugOfferEmailer) entirely unvalidated end-to-end.**
  Compiles clean, logic traced and reasoned through carefully, but ZERO real
  offers have been processed through it yet. Specifically unconfirmed:
  whether Gmail SMTP auth actually succeeds with the App Password, whether the
  email actually arrives, whether the screenshot attachment is present and
  matches the right offer, whether all payload fields populate correctly
  (especially `verdict` — confirmed to be a `Verdict` enum via `.toString()`,
  unconfirmed whether the resulting string is human-readable like "GREEN" or
  something like "Verdict@3f2a")
- **DEBUG_RECIPIENT placeholder value not confirmed updated.** Milo was
  instructed earlier to replace `"you@example.com"` with a real address — not
  independently re-verified this session before session close
- **sheetState/stale-pill fix not validated on real device.** Reasoning is
  sound and traced through actual code, but no live reproduction or
  confirmation test was run before session close
- **AUTO_CAPTURE_MODE default change not validated on real device.** Compiles
  correctly; whether Settings UI actually shows "Fully Automatic" pre-selected
  on the NEXT fresh install (current state, since app data wiped per #7) has
  not been visually confirmed
- Everything from the PREVIOUS session's "Next Session — Start Here" that
  wasn't touched this session remains untouched: OpenCV pin-detection
  pipeline, pixel-to-mile self-calibration math, delivery town estimation via
  GPS bearing (still fundamentally broken per low-speed bearing unreliability,
  separate from the permission issue), Phase 1 scoring redesign
  (GREEN/YELLOW/RED), Registration + Stripe billing, Sentiment Agent repo

---

## Known Broken (do not ignore)

- **Location permission and GPS toggle reset to denied/off** after the
  uninstall this session (#7) — same recurring issue from last session
  (expected Android behavior on uninstall, not a new bug), must be manually
  re-granted before any town-estimation-adjacent testing, though town
  estimation itself remains a known dead-end pending the bearing-reliability
  problem from last session regardless
- **Accessibility service capability registration is now STALE relative to
  the XML config** as of the #6 fix — MUST be manually toggled off/on in
  Settings before testTakeScreenshot() will work, or the identical
  SecurityException will recur. This is a ONE-TIME migration step tied to
  this specific XML change, not a recurring per-restart requirement — Milo
  considered and explicitly decided AGAINST building automatic re-toggle
  logic for this, correctly identified as solving a one-time dev problem with
  permanent (and potentially driver-disruptive) runtime behavior. No code
  change made; this is a manual-step reminder only.
- **Two pre-existing compiler warnings, unrelated to this session, still
  present:**
  1. `AccessibilityOfferReceiver.kt:136` — "Condition 'distance != null' is
     always 'true'" (smart-cast nullability warning, harmless)
  2. `AccessibilityOfferReceiver.kt:224` — "Elvis operator (?:) always returns
     the left operand of non-nullable type Double" (same category)
     Neither blocks the build. Not investigated or fixed this session — flagged
     here so a future session doesn't mistake them for new regressions.
- **OfferOverlayService.kt:434 "Name shadowed: params"** — pre-existing
  warning inside the CAPTURE_DEAD touch-listener block, unrelated to the
  sheetState fix made this session. Cosmetic only.
- **OfferDetectorService.kt has several pre-existing warnings**: unused
  `mode` variable (line 133), and five separate `recycle(): Unit deprecated`
  warnings (lines 212, 234, 267, 406, 459) — all pre-existing, not touched
  this session, build still succeeds.

---

## Next Session — Start Here

1. **CRITICAL FIRST STEP**: manually re-grant location permission AND
   manually toggle the accessibility service off/on in Settings →
   Accessibility → GigLens, BEFORE any testing. Two separate manual steps,
   both required, neither optional, neither has a command-line shortcut.
2. Confirm `DEBUG_RECIPIENT` in `DebugOfferEmailer.kt` is actually set to
   Milo's real email address (not the `you@example.com` placeholder).
3. Confirm `GIGLENS_DEBUG_SMTP_USER` / `GIGLENS_DEBUG_SMTP_PASS` are exported
   in the shell BEFORE any rebuild — if a build runs without these exported,
   BuildConfig gets empty strings and DebugOfferEmailer will silently skip
   sending (logs a warning, does not crash) — confirm via
   `echo $GIGLENS_DEBUG_SMTP_USER` before running deploy.sh if anything seems
   off.
4. On next real shift: connect Pixel via wireless ADB (port changes on
   reboot — check `~/giglens/.pixel_port` or Wireless Debugging settings),
   start filtered logcat:
   `adb -s 10.0.0.110:<port> logcat | grep -E "OfferDetector|AccessibilityOfferReceiver|DebugOfferEmailer|testTakeScreenshot|OfferOverlayService"`
5. Trigger a real offer (or, per Milo's stated plan, search for an offer and
   log off immediately once one appears — sufficient to trigger detection
   without needing to actually run the delivery). Watch logcat for, IN ORDER:
   - `looksLikeOfferScreen: ... → isOffer=true`
   - `testTakeScreenshot() called` → then either `SUCCESS` or a NEW different
     error (if SecurityException recurs, the manual toggle in step 1 wasn't
     done correctly — double check)
   - `ACTION_OFFER_EXTRACTED received`
   - `Score: ... | Verdict: ... | Net: ...` (confirms OfferScorer ran)
   - `DebugOfferEmailer: Debug offer email sent` OR
     `Debug offer email failed: ...` (this is the definitive signal — check
     this BEFORE checking the actual Gmail inbox)
6. If the email sends successfully: open Gmail, confirm the screenshot
   attachment is present and visually shows the DoorDash offer screen
   clearly, and confirm all payload fields rendered as expected (especially
   double-checking the `verdict` field reads as a clean string, not a raw
   enum toString() like "Verdict@hashcode")
7. Separately watch for the overlay pill behavior fix (#9): does the pill show
   gray/idle (expected) rather than stale offer data when first enabling the
   widget toggle, and does it correctly return to idle/hidden state when no
   offer is active, even after any service restarts that may occur during the
   shift?
8. Once steps 4-7 are confirmed working end-to-end, decide: is this debug
   email pipeline ready to inform the OPEN QUESTION from earlier in this
   doc's predecessor — namely, should the pill's delivery-town display be
   reverted to profit-only NOW given known inaccuracy, or held pending further
   screenshot-based validation? This was flagged as undecided last session
   and remains undecided.
9. Continue with previously-scoped, still-untouched work: OpenCV
   pin-detection pipeline (blocked on #4-6 above being validated), Phase 1
   scoring redesign, Registration + Stripe billing, Sentiment Agent repo.

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Confirmed again this session via live logcat |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | Confirmed again this session — pay/distance/restaurant/deliverBy all populated correctly |
| Offer dedup guard (receiver-level) | ✅ Working | Unaffected this session |
| Delivery town estimation (GPS bearing) | ❌ Still broken | Unchanged from last session — known bearing-reliability issue remains unaddressed |
| AccessibilityService.takeScreenshot() | 🔲 Fix applied, NOT YET validated | SecurityException root-caused and fixed (canTakeScreenshot XML flag), but requires manual service re-toggle not yet performed on current install |
| Debug offer-capture email pipeline | 🔲 Built, compiles, NOT YET validated | New this session — DebugOfferEmailer.kt, wired into AccessibilityOfferReceiver |
| Town accuracy tracking infrastructure | ✅ Built (dormant) | Unchanged from last session |
| Overlay pill stale-state bug | 🔲 Root-caused + fix applied, NOT YET validated | New this session — sheetState reset added to OfferOverlayService.kt re-attach paths |
| AUTO_CAPTURE_MODE default | 🔲 Changed to "accessibility", NOT YET visually confirmed | Three drifted fallback defaults reconciled; pre-launch review flag added to FEATURE_BACKLOG.md |
| Accessibility disclosure screen | 🔲 Still not started | Unchanged from last session — now has an ADDITIONAL open question (see FEATURE_BACKLOG.md) about whether default-on capture mode changes its required content |
| Scoring redesign (Phase 1) | 🔲 Scoped | Untouched this session |
| Registration + Stripe billing | 🔲 Scoped | Untouched this session |
| Sentiment Agent (separate repo) | 🔲 Scoped | Untouched this session |

---

## Decisions Made This Session

1. Debug offer-capture email pipeline built as a genuinely separate,
   future-extensible class (`OfferDebugPayload` + `DebugOfferEmailer`) rather
   than inline code, specifically because Milo stated an intent to eventually
   turn this into a per-driver opt-in feature for cross-driver analytics. The
   current version is explicitly NOT consent-gated and should not be extended
   directly into that future feature without adding a destination + consent
   flow first — marked via TODO comment in the source.
2. `ByteArrayDataSource` adopted over `FileDataSource` for the email
   attachment — confirmed via actual compile failure that `FileDataSource` is
   unavailable in the Android JavaMail port, not a guess.
3. SMTP credential fields (`DEBUG_SMTP_USER`/`PASS`) moved from `debug{}` to
   `defaultConfig{}` in build.gradle.kts — chosen over the alternative of
   duplicating the same fields into both `debug{}` and `release{}` separately,
   to avoid future drift between the two declarations.
4. Switched SMTP relay from existing GoDaddy relay to Gmail, per Milo's
   explicit preference — requires a Gmail App Password, not the account
   password directly.
5. `android:canTakeScreenshot="true"` added to accessibility service XML
   config — required, not optional, for `AccessibilityService.takeScreenshot()`
   to function at all. This corrects a previously-stated (and wrong) claim
   from the prior session's handover that no additional capability
   declaration was needed.
6. Confirmed and documented: capability changes to the accessibility service
   XML config require a MANUAL re-toggle of the service in Android Settings
   to take effect — a code/manifest change alone is insufficient, since
   Android caches capabilities at last-enabled time, not at app-restart time.
7. Explicitly decided AGAINST building automatic forced re-toggle logic
   triggered by app restart — correctly identified by Milo, after pushback
   and clarification, as solving a one-time dev-only migration problem with
   permanent runtime behavior that would disrupt real drivers on ordinary app
   restarts (backgrounding, low memory, etc.) for a problem they would never
   actually encounter, since they don't edit the XML config themselves.
8. `AUTO_CAPTURE_MODE` default changed to `"accessibility"` (fully automatic)
   per Milo's explicit personal preference for his own dev/testing workflow.
   Explicitly flagged, NOT decided, whether this should also be the default
   for real drivers on the eventual public release — tracked as an open
   pre-launch review item in FEATURE_BACKLOG.md, tied directly to the
   still-unbuilt disclosure screen's required content.
9. Overlay pill stale-state bug fix: reset `sheetState` to `IDLE` specifically
   at the two silent window re-attachment points in `onStartCommand`, rather
   than any broader rework of the overlay state machine — minimal, targeted
   fix scoped to the actual traced failure path, not a speculative rewrite.
10. From earlier in session: confirmed via direct compilation test (a
    standalone Kotlin snippet, compiled and run with `kotlinc`/`java` in the
    sandbox) that the triple-quoted-string pattern used for generating
    `buildConfigField` string literals (`""""$var""""`) is valid Kotlin and
    evaluates correctly — chosen specifically because it avoids ambiguous
    nested-quote escaping that could not be confidently verified by inspection
    alone.

---

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL: wireless ADB port changed MULTIPLE times this session due to
  reinstalls — always check `~/giglens/.pixel_port` or Wireless Debugging
  settings fresh at session start, do not assume a port from a previous
  session or even from earlier in the same session is still valid after any
  reinstall/redeploy
- App was UNINSTALLED and FRESH-INSTALLED once this session (see #7) — all
  permissions, accessibility grants, and local DB data were wiped as a result
  and must be re-established from scratch on next launch
- Keystore: ~/giglens/giglens-release.keystore (unchanged)
- Play Store JSON key: ~/giglens/google-play-api-key.json (DO NOT COMMIT,
  unchanged)
- NEW environment variables required for builds going forward:
  `GIGLENS_DEBUG_SMTP_USER`, `GIGLENS_DEBUG_SMTP_PASS` (Gmail address + Gmail
  App Password respectively) — must be exported in the shell BEFORE running
  any Gradle build, same shell-session requirement as the existing
  `GIGLENS_STORE_PASSWORD`/`GIGLENS_KEY_PASSWORD`. Recommend adding to
  `~/.bashrc` alongside the existing MOBSF API key, if not already done.

## Files Changed This Session

- `app/src/main/java/com/augusteenterprise/giglens/debug/DebugOfferEmailer.kt`
  (NEW FILE — debug-only offer capture emailer)
- `app/src/main/java/com/augusteenterprise/giglens/service/AccessibilityOfferReceiver.kt`
  (patched — DebugOfferEmailer.sendAsync() call added inside `if (result != null)` block)
- `app/build.gradle.kts` (patched multiple times this session — JavaMail
  dependencies added; SMTP buildConfigField entries added then migrated from
  debug{} to defaultConfig{}; packaging.resources.excludes block added for
  duplicate META-INF files)
- `app/src/main/res/xml/accessibility_service_config.xml` (patched —
  `android:canTakeScreenshot="true"` added)
- `app/src/main/java/com/augusteenterprise/giglens/data/AppConfig.kt`
  (patched — AUTO_CAPTURE_MODE seed default changed "off" → "accessibility")
- `app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt`
  (patched — AUTO_CAPTURE_MODE null-fallback changed "tap" → "accessibility",
  for consistency with the other two fallback locations)
- `app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt`
  (patched — sheetState reset to IDLE added at both silent window re-attach
  paths in onStartCommand, fixing stale-pill-data bug)
- `docs/FEATURE_BACKLOG.md` (appended — new section flagging the
  AUTO_CAPTURE_MODE default change as a pre-launch review item, tied to the
  disclosure screen decision)

Multiple `./deploy.sh` runs this session (version progressed from 0.1.162
seed state through to 0.1.165 by session end, via deploy.sh's auto-bump —
exact intermediate version at each individual fix is not separately tracked,
only the final state matters for next session).

---

## Features Left to Implement (Priority Ranked)

See docs/FEATURE_BACKLOG.md for full list. Top of stack as of this session end:

1. **Live-validate everything built this session** on next real shift — this
   single item gates nearly everything else: testTakeScreenshot() success,
   debug email arrival + content correctness, overlay pill fix correctness,
   AUTO_CAPTURE_MODE default visual confirmation
2. **Decide pill display** (town vs profit-only) — still undecided, carried
   forward from last session, now also gated on this session's screenshot
   validation
3. **Decide AUTO_CAPTURE_MODE default for real drivers** — new open question
   this session, tied to disclosure screen content, must be resolved before
   Play Store submission prep
4. **Accessibility disclosure screen** — rebuild with ViewBinding, now with an
   additional open question about default-capture-mode language
5. **OpenCV pin-detection pipeline** — still blocked on screenshot validation
6. **Scoring redesign (Phase 1)** — still queued, untouched this session
7. **Registration + Stripe billing** — still queued, untouched this session
8. **Sentiment Agent repo** — still queued, untouched this session

---

*Next developer: read SESSION_PROTOCOL.md and WORKING_AGREEMENTS.md first,
then return here. PRIORITY: this session built three substantial new pieces
(debug email pipeline, takeScreenshot capability fix, overlay stale-state fix)
that are ALL compile-verified but ZERO percent runtime-validated. Do not
assume any of them work until confirmed via live logcat on a real shift —
this is not a formality, it is the literal first thing that needs to happen
next session, per this project's own verify-don't-assume working agreement.*
