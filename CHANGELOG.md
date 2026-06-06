## [Unreleased] - 2026-06-06
### Changed
- ScreenCaptureService: VirtualDisplay now created per-capture and destroyed immediately after — from 3600s held to ~2s per offer (99.8% reduction)
- ScreenCaptureService: MediaProjection token held only — no screen mirror between offers
- ScreenCaptureService: added releaseDisplayResources() and createDisplayResources() helpers
- ScreenCaptureService: watchdog now checks mediaProjection only — VirtualDisplay intentionally null between captures
- ScreenCaptureService: added 200ms delay after VirtualDisplay creation to ensure first frame renders
### Fixed
- Memory pressure mid-shift: VirtualDisplay held entire shift was consuming ~8MB constant — now freed between offers

## [Unreleased] - 2026-06-06
### Fixed
- ScreenCaptureService: reduced ImageReader buffer from 2 frames to 1 — saves ~8MB constant memory at 1440p
- ScreenCaptureService: downsample capture bitmap to 50% before OCR — reduces memory by 75% with no accuracy loss
### Root cause identified
- ScreenCaptureService dying mid-shift due to system mem-pressure-event — Android killing services under memory load
- DoorDash + MediaProjection + navigation simultaneously pushes Pixel 10 to memory limit
- Above fixes reduce GigLens memory footprint to help survive memory pressure events

## [Unreleased] - 2026-06-06
### Fixed
- OfferOverlayService: now calls startForeground() on all Android versions — prevents Android from killing overlay mid-shift
- OfferOverlayService: added CAPTURE_DEAD state — red ⚠️ Tap pill stays visible when ScreenCaptureService dies instead of disappearing
- OfferDetectorService: signals OfferOverlayService when ScreenCaptureService is dead — pill updates immediately
- MainActivity: handles restart_capture intent flag — tapping CAPTURE_DEAD pill relaunches MediaProjection dialog automatically

## [Unreleased] - 2026-06-04
### Fixed
- OfferDetectorService: removed blanket ACTION_SHOW_CAMERA on every window event — camera button no longer blinks on map redraws, navigation updates, or earnings screen
- OfferDetectorService: SHOW_CAMERA now only fires on confirmed new offer screen detection
- OfferDetectorService: HIDE_CAMERA sent when offer screen disappears — camera button clears cleanly

## [Unreleased] - 2026-06-04
### Fixed
- OfferOverlayService: disabled auto-revert to IDLE — pill now persists for entire shift duration
- OfferOverlayService: removed countdown timer from pill label — no longer shows Xs during shift
- ScreenCaptureService: start OfferOverlayService immediately on capture launch — pill now visible from shift start without requiring MainActivity interaction
### Changed
- Pill behavior: result state (TAKE/BORDERLINE/SKIP) persists until next offer replaces it — never auto-clears mid-shift

## [Unreleased] - 2026-06-04
### Fixed
- OfferDetectorService: wrapped rootNode in try/finally — guarantees recycle() even if offer detection throws mid-walk
- OfferDetectorService: replaced runBlocking DB reads in onAccessibilityEvent() and signalCapture() with cached values populated at onServiceConnected() — eliminates ANR risk on accessibility thread
- OfferOverlayService: added stopSelf(startId) to ACTION_SHOW_CAMERA and ACTION_HIDE_CAMERA handlers — releases transient ConnectionRecords, fixes ~25 DEAD entry leak
- OfferDetectorService: removed duplicate startService(ACTION_SHOW_CAMERA) inside signalCapture() — was creating second orphaned record per event
- ScreenCaptureService: replaced watchdog acquireLatestImage() health check with lightweight virtualDisplay/mediaProjection null checks — eliminates unnecessary pixel buffer allocation every 30s
- ScreenCaptureService: added textRecognizer.close() in onDestroy() — releases ML Kit native handles on service teardown

## [Unreleased]
### Fixed
- Grey idle pill now appears immediately when toggle is ON (was only appearing after DoorDash opened)
- Root cause: onResume was gating on ScreenCaptureService.isRunning instead of overlay permission
- Overlay permission (SYSTEM_ALERT_WINDOW) added as step 0 of onboarding flow
- BadTokenException crash fixed — showWidget() now catches permission denied gracefully
- canDrawOverlays() false-negative on fresh process handled via pendingOverlayForOnboarding flag

## [Unreleased]
### Fixed
- Camera button now appears on DoorDash without visiting Settings
- Root cause: OfferDetectorService was using sendBroadcast() to signal OfferOverlayService,
  but OfferOverlayService handles signals via onStartCommand() not a BroadcastReceiver.
  Fixed by switching to startService(Intent(...).apply { action = ACTION_SHOW_CAMERA }).
- Added 2s cooldown on SHOW_CAMERA to prevent startService spam on every content change event.
- DB seed defaults changed: AUTO_CAPTURE_MODE default "off" → "button", WIDGET_ENABLED "false" → "true"

## [0.1.56] - 2026-05-24
### Note
- Stop-sign octagon for SKIP deferred — keeping red rounded pill for now

## [0.1.55] - 2026-05-24
### Changed
- Feature #8: Removed countdown blink — timer now displays statically

## [0.1.54] - 2026-05-24
### Changed
- Feature #8: Only the timer segment (· 8s) blinks under 10s — net value stays solid white

## [0.1.53] - 2026-05-24
### Fixed
- Feature #8: Countdown now persists when pill expanded to MINI/FULL (was disappearing on tap)
### Added
- Feature #8: Countdown blinks (alternating alpha) when under 10 seconds for urgency

## [0.1.52] - 2026-05-24
### Added
- Feature #8: Countdown timer now displayed on the result pill (e.g. +$5.40 · 58s)
- Feature #8: Result display duration configurable in FLOATING WIDGET settings (default 60s, range 5-300s)
- DB Migration 5→6 — seeds result_display_seconds config key

## [0.1.51] - 2026-05-24
### Added
- Feature #8: Result pill auto-reverts to IDLE after 60s if no new offer
- Feature #8: Driver interaction (tap to expand) restarts the 60s timer
- Feature #8: New offer detected cancels revert timer + morphs to camera
- Feature #8: OfferDetectorService fingerprints offers — same offer won't re-trigger camera

## [0.1.50] - 2026-05-23
### Added
- DEBUG ONLY: DebugTriggerReceiver — lets ADB trigger widget states for testing
- ADB usage: am broadcast -a com.augusteenterprise.giglens.DEBUG_TRIGGER --es state [camera|hide|offer]
### Note
- DebugTriggerReceiver must be removed or guarded before release build

## [0.1.49] - 2026-05-23
### Added
- Feature #8: Widget morph — IDLE/CAMERA/PROCESSING/PILL/MINI/FULL states
- Feature #8: OfferOverlayService handles ACTION_SHOW_CAMERA + ACTION_HIDE_CAMERA intents
- Feature #8: Camera button tap triggers manual capture broadcast
- Feature #8: OfferDetectorService morphs widget to camera on offer detection
- Feature #8: Auto-capture only triggered when mode = accessibility or both

## [0.1.48] - 2026-05-23
### Security
- Created SECURITY.md — documents false positives, accepted risks, fixed issues
- MobSF pre-commit output now shows score ceiling context (debug ~47, release ~75)
- Documented Room DB schema hashes as known false positives

## [0.1.47] - 2026-05-23
### Security
- Manifest: android:allowBackup set to false — prevents ADB data extraction
- Manifest: BootReceiver exported=false — BOOT_COMPLETED delivered without public export
- Manifest: ShareReceiverActivity permission protected with MANAGE_DOCUMENTS
- AppConfig: Added @Suppress annotation to silence MobSF false positive on DB key strings

## [0.1.36] - 2026-05-23
### Added
- Feature #8: CaptureButtonService — floating teal camera button over gig apps
- Button is draggable, flashes green on tap to confirm capture triggered
- CaptureButtonService auto-starts/stops when capture mode is saved in Settings
- CaptureButtonService registered in AndroidManifest with specialUse foreground type

## [0.1.35] - 2026-05-23
### Added
- Feature #8: PlatformRegistry.kt — defines DoorDash (active) + Uber Eats/Grubhub/Instacart (coming soon)
- Feature #8: AUTO_CAPTURE_MODE config key — off | accessibility | button | both
- Feature #8: ENABLED_PLATFORMS config key — comma-separated active platform IDs
- Feature #8: Settings UI — AUTO CAPTURE card with 4 mode options + accessibility permission button
- Feature #8: Settings UI — SUPPORTED PLATFORMS card (DoorDash enabled, others grayed out)
- Feature #8: OfferDetectorService now checks auto_capture_mode + enabled_platforms before triggering
- DB Migration 4→5 — seeds auto_capture_mode, enabled_platforms, hourly_rate

## [0.1.35] - 2026-05-23
### Fixed
- OfferOverlayService: New shares now reuse existing pill instead of adding a duplicate (isViewAdded flag)
- Each new offer updates the existing widget in place via updateWidget()

## [0.1.34] - 2026-05-22
### Fixed
- Widget full detail now shows configured cost-per-mile rate in vehicle cost label (e.g. "Vehicle ($0.90/mi)")
- costPerMileUsed from ScoreResult now passed through intent to overlay service

## [0.1.33] - 2026-05-22
### Fixed
- OfferScorer: Added timeCost, totalCost, minutesOnJob fields to ScoreResult (Bug A)
- OfferScorer: Net value now deducts both vehicle cost AND time cost (full formula)
- ShareReceiverActivity: Now passes EXTRA_TIME_COST, EXTRA_TOTAL_COST, EXTRA_MINUTES_ON_JOB, EXTRA_SCORE to overlay service (Bug B)
- Widget full detail now shows correct time cost, total cost, and score

## [0.1.32] - 2026-05-22
### Added
- Settings UI: hourly rate field added to SCORING card (Issue #3)
- Drivers can now configure their hourly rate (default $15.00/hr) which affects time cost calculation

## [0.1.31] - 2026-05-22
### Fixed
- ShareReceiverActivity: replaced deprecated getParcelableExtra<Uri>() with type-safe getParcelableExtra(Uri::class.java) (Issue #2)

## [0.1.29] - 2026-05-22
### Fixed
- Implemented GeocodingHelper.loadStateNameMap() — previously referenced but missing
- Written-out state names (e.g. "New Jersey") now correctly detected in extractRegionHint()
- STATE_NAME_MAP now populated from assets/state_name_map.json at app startup

### Added
- assets/state_name_map.json — all 50 states + DC, name-to-abbreviation mapping


## [0.1.8] - 2026-05-20

### Fixed
- OfferParser: Strategy 1 restaurant extraction now skips "Center", "Township", "Guaranteed", "incl" lines that were being returned instead of restaurant name
- OfferParser: Strategy 2 restaurant extraction now rejects UUID/hex garbage lines and number-only lines
- StreetExtractor: extractNearKeyword now scans 10 lines BEFORE keyword (nearest first) — fixes pickup street extraction where DoorDash renders street above Pickup label on screen
- StreetExtractor: isStreetLine secondary match added to catch OCR-fragmented addresses without street suffix tokens

### Added
- OfferParser: extractCurrentTime() — parses current time from OCR status bar with AM/PM inference from deliver-by time
- OfferParser: extractDeliverByMinutes() — returns deliver-by as total minutes since midnight for time math
- OfferParser: estimateDeliveryLegMiles() — estimates pickup→dropoff distance using time remaining, pickup distance, and avg speed (45 mph default)
- OfferParser: DeliveryEstimate data class — holds delivery leg, total miles, time remaining, status (OK/EXPIRED/IMPOSSIBLE)
- GeocodingHelper: extractRegionHint() — extracts town/state from raw OCR map labels for Nominatim proximity anchor
- GeocodingHelper: estimateDeliveryDistance() now accepts rawOcrText and auto-extracts region hint when GPS unavailable

### Infrastructure
- tools/ocr_testapp/ — standalone Android test app for ML Kit OCR + parser validation on emulator without phone
- tools/ocr_test.py — Linux Tesseract harness for fast parser iteration
- tools/run_ocr.sh — venv wrapper for ocr_test.py
- Android emulator (giglens_test AVD) configured on milo-dev with KVM acceleration
# security hook test
# retest
# deep scan test
# mobsf test
# full deep scan test
# deep scan test
# owasp graceful test
# owasp 403 fix
# owasp clean rewrite test
# mobsf rescan

## [0.1.66] - 2026-06-01
### Fixed
- MediaProjection.Callback registration now required before createVirtualDisplay() on Android 14+ — fixes IllegalStateException crash on Pixel 10 Pro XL
- Bitmap recycle race condition — bitmap was being recycled before ML Kit OCR completed, causing "Failed to lock pixels" error
- Switched ML Kit dependency from bundled to GMS-based (play-services-mlkit-text-recognition:19.0.0) for Android 16 compatibility
- targetSdk bumped from 34 to 35 — required for Play Store submission
- AndroidManifest icon reference fixed from system default to @mipmap/ic_launcher
- App icon added — all mipmap densities (mdpi through xxxhdpi) plus round variants

### Added
- Play Store Internal Testing track — GigLens v0.1.66 published
- 512x512 Play Store listing icon

### Security
- Removed keystores and sensitive files from git tracking
- Updated .gitignore to prevent future accidental commits of keystores and temp files

## [0.1.67] - 2026-06-01
### Changed
- Consolidated screen capture + floating button into single Settings toggle — no more separate "Enable Auto Mode" button in MainActivity
- MainActivity simplified to status card only — shows capture state with color indicator
- Floating button radio in Settings now triggers MediaProjection permission automatically

### Fixed
- ScreenCaptureService double-stop bug — nulling references before stopSelf() prevents crash in onDestroy()
- CaptureButtonService now flashes red and shows toast when ScreenCaptureService is not running
- Bitmap recycle race condition in ScreenCaptureService

### Added
- MPG and gas price fields in Settings for gas-based vehicle cost calculation
- Vehicle fuel cost replaces flat IRS cost-per-mile in scorer
- EPA fueleconomy.gov link in Settings for MPG lookup

## [0.1.72] - 2026-06-02
### Fixed
- Overlay drawer label now shows "Vehicle (gas cost)" instead of misleading "$0.90/mi" rate

### Fixed
- extractRestaurant(): reject time patterns (\d{1,2}:\d{2}), high digit ratio candidates, min length 4 — fixes "D 44:55" and "G l" misreads
- ScreenCaptureService: wire showRestartNotification() to MediaProjection.onStop() — driver now gets tappable notification when session expires
- ScreenCaptureService: session health watchdog — detects 3 consecutive null frames, triggers restart notification proactively before silent failure

## [v0.1.74] - 2026-06-02

### Added
- MainActivity full analytics dashboard redesign
  - Controls card: floating button toggle, auto capture toggle, offer history row, settings row
  - Today stats: offer count, avg pay, avg $/mile
  - Earnings overview card: weekly net earnings, avg net/offer, avg gas cost, true $/mile, avg distance
  - Analytics chart: Chart.js line chart via WebView, 7d/30d/All tab switching
  - Verdict breakdown: TAKE/BORDERLINE/SKIP progress bars with counts and percentages
  - Recent offers: last 3 offers with verdict badge, restaurant, pay/distance/score, net value
  - LIVE badge with smooth ObjectAnimator ease-in-out blink (not harsh flash)
- DailyNetValue.kt — new Room projection class for analytics chart data
- OfferCaptureDao: 14 new query methods (today stats, weekly earnings, verdict counts, chart data, best/avg/worst net, recent offers)
- OfferCapture: added timeCost and minutesOnJob fields
- OfferDatabase: version 7, MIGRATION_6_7 adds timeCost + minutesOnJob columns

### Fixed
- extractRestaurant(): time pattern rejection, digit ratio guard, minimum length 4 — fixes "D 44:55" and "G l" misreads
- ScreenCaptureService: showRestartNotification() now fires when MediaProjection session expires
- ScreenCaptureService: health watchdog detects 3 consecutive null frames and triggers restart notification proactively

## [v0.1.79] - 2026-06-02

### Added
- MainActivity full analytics dashboard (today stats, earnings overview, Chart.js analytics, verdict breakdown, recent offers)
- LIVE/OFF badge with smooth ObjectAnimator animation
- Onboarding flow: accessibility service check → screen capture permission → pill + camera button
- SharedPreferences-based toggle persistence across onResume cycles
- OfferOverlayService.isRunning flag for safe overlay start gating

### Fixed
- OfferOverlayService: Android 12+ ForegroundServiceStartNotAllowedException — service now started from MainActivity foreground context
- OfferDetectorService: "button" mode was incorrectly skipping SHOW_CAMERA — now only skips on "off"
- Toggle reset loop: updateUI() no longer resets toggle to OFF during permission grant flow
- AUTO_CAPTURE_MODE written to DB immediately on toggle ON — OfferDetectorService sees correct mode
- LIVE badge background set programmatically (GradientDrawable) — was blank in XML

### Known issues
- Settings capture mode radio does not auto-select after enabling from MainActivity — requires manual save in Settings (UX redesign planned next session)
