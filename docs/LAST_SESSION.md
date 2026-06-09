# GigLens — Session Handover
**Date:** 2026-06-09
**Version at session end:** 0.1.140
**Build state:** PASSING
**Conducted by:** Claude (Anthropic) — manual session

---

## What Was Completed This Session

### 1. Real-world shift validation
- Scoring worked correctly on live DoorDash offers
- Camera button morphed correctly when live offer appeared
- Orders were captured and charted (charting needs improvement)
- Countdown timer worked but needs improvement
- Identified pill appearing on non-offer DoorDash screens (fixed this session)

### 2. Fastlane command-line deploy pipeline (NEW)
- Installed Ruby 3.4.9 via rbenv on milo-dev
- Installed Fastlane 2.236.0
- Created Google Cloud service account: giglens-deploy@gen-lang-client-0130402808.iam.gserviceaccount.com
- Enabled Google Play Android Developer API on project gen-lang-client-0130402808
- Granted service account Release Manager access in Play Console
- JSON key stored at: ~/giglens/google-play-api-key.json
- Created ~/giglens/deploy.sh — one command builds AAB + uploads to Internal Testing
- Deploy command: ./deploy.sh "changelog message"

### 3. Bug fix — countdown disappears on expand (v139)
- File: OfferOverlayService.kt
- Root cause: netLabelSpannable() gated countdown on `sheetState == SheetState.PILL`
- When pill expanded to MINI or FULL, condition failed and countdown dropped
- Fix: removed `sheetState == SheetState.PILL` guard — countdown now shows in all expanded states

### 4. Bug fix — pill appearing on non-offer DoorDash screens (v139)
- File: OfferDetectorService.kt
- Root cause: packageNames filter was commented out in onServiceConnected()
- All DoorDash screen events flowed through, not just offer screens
- Fix 1: re-enabled `packageNames = arrayOf("com.doordash.driverapp")` filter
- Fix 2: added explicit package check at top of onAccessibilityEvent() — non-DoorDash
  events now send HIDE_CAMERA and return immediately
- Result: pill only appears when looksLikeOfferScreen() returns true on DoorDash

### 5. versionName sync fix (v139)
- versionCode and versionName were out of sync (code=138, name=0.1.136)
- Root cause: pre-commit hook sed pattern for versionName had failed silently
- Manually resynced to versionCode=138 / versionName=0.1.138
- Confirmed hook now bumps both correctly on each commit

### 6. Bug fix — screen share dialog timer on accessibility mode (v140)
- File: SettingsActivity.kt
- Root cause: saveSettings() launched createScreenCaptureIntent() for ALL modes
  including "accessibility" — triggered Android screen recording countdown dialog
- Fix: added explicit "accessibility" branch in when(savedMode) that skips
  MediaProjection entirely and just shows "Settings saved" toast
- TODO marker left in code for when ScreenCaptureService is fully removed

---

## What Was Left Incomplete

- Order archive view (day/week/month) — not started
- Chart query scope fix — chart shows 10 orders but only 3 completed this shift;
  old DB records from previous sessions not being displayed correctly
- Play Store update not confirmed on Pixel — propagation still pending at session end
- Fastlane Ruby warning not resolved — fastlane at /usr/local/bin still uses system
  Ruby 3.2.3; rbenv 3.4.9 not wired into deploy.sh path yet

---

## Known Broken (do not ignore)

- Chart shows ALL captured orders (including previous sessions) not just completed ones
  → 10 shown vs 3 actually completed this shift
- Pill screen targeting fix (v139) not yet validated on real device — deploy still
  propagating to Play Store at session end
- Fastlane Ruby warning: "Support for Ruby 3.2.3 is going away" on every deploy
  → harmless for now but needs Gemfile fix before fastlane drops 3.2 support

---

## Next Session — Start Here

1. Confirm v140 installed on Pixel — validate pill no longer appears on non-offer screens
2. Confirm screen share dialog no longer appears when accessibility mode selected
3. Pull order/chart DB files and fix archive query:
   - Find chart query file: grep -rn "chart\|Chart\|history\|History" ~/giglens/app/src
   - Fix scope to show completed orders only vs all captured
   - Build day/week/month archive view
4. Fix Fastlane Ruby path:
   - Add Gemfile to ~/giglens/ pointing to Ruby 3.4.9
   - Or: update deploy.sh to use rbenv shim path

---

## Active Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| Offer detection (looksLikeOfferScreen) | ✅ Working | Validated on real shift |
| Accessibility extraction (extractOfferFromNodes) | ✅ Working | pay, distance, restaurant, countdown all extracted |
| Camera button morph | ✅ Working | Appears on live offer, validated on shift |
| Countdown timer on pill | ✅ Working | Fixed this session — now visible in MINI/FULL states |
| Pill screen targeting | ✅ Fixed, unvalidated | v139 — awaiting device install |
| Screen share dialog suppression | ✅ Fixed | v140 — accessibility mode skips dialog |
| Order charting | ⚠️ Partial | Captures recorded but chart scope incorrect |
| Order archive (day/week/month) | 🔲 Not started | Next priority |
| ScreenCaptureService removal | 🔲 Planned | Dedicated session — 2-3 hour job |
| Fastlane Ruby fix | ⚠️ Minor | Warning only, not blocking |
| Play Store deploy pipeline | ✅ Working | deploy.sh one-command build+ship |

---

## Decisions Made This Session

1. **Keep ScreenCaptureService for now** — removal is a 2-3 hour dedicated session.
   Roots in 5 files + manifest. Small fix applied instead (accessibility branch).
   TODO markers left in code at all removal points.

2. **Accessibility mode is the recommended default** — validated on real shift.
   OCR/MediaProjection is fallback only. Plan to remove entirely next dedicated session.

3. **Fastlane over manual Play Store uploads** — pipeline established, JSON key at
   ~/giglens/google-play-api-key.json. Do not commit key to git (already in .gitignore).

4. **versionName must stay in sync with versionCode** — pre-commit hook handles both.
   Never manually edit either field — always let the hook bump them.

---

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL (10.0.0.110:PORT): check Wireless Debugging each session — port changes on reboot
- Samsung S20 (10.0.0.189:5555): run `adb connect 10.0.0.189:5555` to verify
- Keystore: ~/giglens/giglens-release.keystore
- Play Store JSON key: ~/giglens/google-play-api-key.json (DO NOT COMMIT)
- Ruby: rbenv 3.4.9 at ~/.rbenv/versions/3.4.9
- Fastlane: 2.236.0 at /usr/local/bin/fastlane (system Ruby — fix with Gemfile next session)

## Files Changed This Session

- app/src/main/java/com/augusteenterprise/giglens/service/OfferOverlayService.kt
- app/src/main/java/com/augusteenterprise/giglens/service/OfferDetectorService.kt
- app/src/main/java/com/augusteenterprise/giglens/ui/SettingsActivity.kt
- app/build.gradle.kts
- version.txt
- deploy.sh (NEW)
- metadata/en-US/changelogs/ (NEW)
- metadata/en-US/title.txt, short_description.txt, full_description.txt, video.txt (NEW)

---

## Features Left to Implement (Priority Ranked)

### High Priority
1. **Order archive view** — day/week/month grouping UI + fix chart query scope
2. **ScreenCaptureService removal** — dedicated session, clean up 5 files + manifest
3. **Pill screen targeting validation** — confirm fix works on real device

### Medium Priority
4. **Fastlane Ruby fix** — add Gemfile, wire rbenv 3.4.9 into deploy path
5. **In-app update prompt** — notify driver before shift starts if update available
6. **Countdown UX improvement** — driver feedback: needs better urgency signal

### Low Priority / Tabled
7. **Robolectric/Espresso automated testing** — tabled from prior session
8. **Android 14 partial screen sharing** — reduces MediaProjection token expiry risk;
   irrelevant if ScreenCaptureService is removed
9. **ConnectionRecord leaks** — ~25 dead entries from OfferDetectorService, low impact

---

*Next developer: read SESSION_PROTOCOL.md first, then return here.*
