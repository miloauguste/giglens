# Pickup/Dropoff Distance Research
**Author:** Milo Auguste Jr. / Claude (Anthropic)
**Date:** 2026-06-05
**Status:** Parked — logic removed from pipeline, preserved here for future implementation

---

## Why This Was Removed

The geocoding pipeline was removed from `ScreenCaptureService.runOcr()` and `ShareReceiverActivity` in commit after v0.1.94 for the following reasons:

1. DoorDash hides the dropoff address until after acceptance — OCR sees "Customer dropoff" only
2. Two API calls per offer (Places + Distance Matrix) added latency on a 35-second timer
3. More failure points — GPS + OCR address extraction + Places API + Distance Matrix all had to succeed
4. Initial assumption was DoorDash distance = full trip (driver → pickup → dropoff)

## The Problem With DoorDash's Distance

DoorDash's displayed mileage (e.g. "3.7 mi") is **not reliably the full trip**. It tends to be:
- The delivery leg only (restaurant → customer) in some markets
- The full trip (driver → restaurant → customer) in others
- Inconsistent across offer types (stacked orders, large orders, etc.)

This means vehicle cost and net value calculations based on DoorDash's distance alone are inaccurate.

---

## Removed Code — ScreenCaptureService.runOcr()

The following logic was removed and should be reimplemented:

```kotlin
// Get driver location
val location = LocationHelper.getCurrentLocation(applicationContext)

// Extract street addresses from OCR text
val addresses = StreetExtractor.extract(rawText)

// Estimate delivery distance via geocoding
val distanceEstimate = GeocodingHelper.estimateDeliveryDistance(
    pickupStreet  = addresses.pickupStreet,
    dropoffStreet = addresses.dropoffStreet,
    regionHint    = null
)

// Calculate pickup distance from driver to restaurant
val pickupDistance: Double? =
    if (location != null && distanceEstimate.pickupPoint != null) {
        LocationHelper.straightLineDistance(
            location.latitude, location.longitude,
            distanceEstimate.pickupPoint.lat,
            distanceEstimate.pickupPoint.lon
        ) * 1.3  // 1.3x multiplier converts straight-line to road distance estimate
    } else null
```

---

## Future Implementation Plan

### What DoorDash Shows on the Offer Screen
From OCR analysis of real offer screenshots:
- Restaurant name (e.g. "Taco Bell") — always visible
- "Customer dropoff" — no address shown
- Mileage (e.e "3.7 mi") — shown but unreliable
- Pay amount (e.g. "$7.20") — always visible
- Delivery timer (e.g. "Deliver by 12:04 AM") — always visible

### Deriving Pickup Address
DoorDash shows the restaurant name. Strategy:
1. OCR extracts restaurant name via `OfferParser`
2. Google Places API `findPlaceFromText()` with driver's current location as bias
3. Returns the nearest matching restaurant with full address + coordinates
4. Driver location → restaurant coordinates = real driving pickup distance via Distance Matrix API

**Challenge:** Chain restaurants have multiple locations. Need location bias to pick the right one.
**Solution:** Use driver's GPS coordinates as the Places API location bias radius (e.g. 5 miles).

### Deriving Dropoff Address
DoorDash intentionally hides this. Potential strategies:

**Strategy 1 — Delivery timer inference**
- "Deliver by 12:04 AM" + current time = time budget
- time_budget × average_speed = rough distance estimate
- Low accuracy but zero API calls

**Strategy 2 — Map screenshot analysis**
- The offer screen shows a map with a route line drawn
- Route start = restaurant pin, route end = dropoff pin
- ML Kit or custom model could extract pin coordinates from the map image
- Convert screen coordinates → GPS coordinates using map zoom/center
- High accuracy but complex implementation

**Strategy 3 — Historical pattern matching**
- After enough accepted offers, build a local model of:
  restaurant → typical delivery radius in that area
- Use personal history to estimate dropoff distance
- Gets more accurate over time, zero API calls after warmup

### Recommended Approach (Phase 2)
1. **Pickup:** Google Places API with restaurant name + driver location bias → real driving distance
2. **Dropoff:** Strategy 1 (timer inference) as a starting estimate, Strategy 3 (historical) as it matures
3. **Override:** Always allow driver to manually input actual distance after completing an offer

### API Costs
- Google Places `findPlaceFromText`: $17/1000 requests
- Google Distance Matrix: $10/1000 elements
- At 50 offers/shift × 20 shifts/month = 1000 requests/month ≈ $27/month
- Consider caching restaurant lookups — same Taco Bell appears repeatedly

---

## Related Files
- `StreetExtractor.kt` — OCR address extraction (preserved, not deleted)
- `GeocodingHelper.kt` — distance estimation via geocoding (preserved, not deleted)
- `LocationHelper.kt` — GPS location + straight-line distance (preserved, used for future dashboard)
- `OfferParser.kt` — extracts restaurant name, pay, distance from OCR text
- `OfferScorer.kt` — scoring engine, `pickupDistance` parameter removed but can be re-added

---

## Re-Integration Checklist (when ready)
- [ ] Restore `pickupDistance` parameter to `OfferScorer.score()`
- [ ] Restore `pickupDistance` field to `ScoreResult`
- [ ] Re-wire `LocationHelper` + `StreetExtractor` + `GeocodingHelper` in `runOcr()`
- [ ] Add Google Places restaurant lookup for pickup address
- [ ] Add delivery timer parser to `OfferParser`
- [ ] Add API cost monitoring — alert if monthly spend exceeds threshold
- [ ] Update `OfferCapture` DB schema to store pickup/dropoff coordinates
