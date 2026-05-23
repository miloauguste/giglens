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
