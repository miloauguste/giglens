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
