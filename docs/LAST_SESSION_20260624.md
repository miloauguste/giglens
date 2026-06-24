# GigLens — Session Handover (2026-06-24)

---

## Session 1 — Post-shift town investigation + GPS warning (2026-06-24)
**Version at session start:** 0.1.253
**Version at session end:** 0.1.254
**Conducted by:** Claude (Anthropic)

---

## What Was Investigated

### Post-shift DB pull — 3 offers, all bad town results
Pulled `offer_database` via `adb exec-out` + WAL checkpoint. Found:

| ID | Restaurant | Pay | Distance | Town | Method |
|----|-----------|-----|----------|------|--------|
| 1 | Wawa | $7.50 | 4.0mi | — | unavailable |
| 2 | 7-Eleven | $3.50 | 2.7mi | — | unavailable |
| 3 | McDonald's | $4.75 | 7.9mi | **Vacarisses** | pin_detection |

`driverLat`/`driverLon` null on ALL rows.

### Root cause 1 — GPS permission revoked on reinstall
`adb shell dumpsys package` confirmed:
```
ACCESS_FINE_LOCATION: granted=false
ACCESS_COARSE_LOCATION: granted=false
```
Yesterday's uninstall/reinstall wiped the runtime permission grant. Driver never re-granted it. `LocationHelper.getCurrentLocation()` silently returned null for every offer all shift.

**Fix:** Granted via ADB (`pm grant`). Confirmed `granted=true` immediately after.

### Root cause 2 — Global restaurant geocoding when GPS is null
`DeliveryTownEstimator.resolveByCity()` had this path:
```kotlin
val query = if (city != null) "$name, $city" else name  // ← global search when city=null
```
When `driverLocation` is null → `city` is null → Nominatim searched globally for "McDonald's" → returned first worldwide result (a McDonald's near Barcelona) → pin detection projected 7.73mi from Spain → landed in Vacarisses, Catalonia.

**Fix:** `resolveByCity()` now returns `null` immediately when `city` is null. Worst case going forward is `unavailable`, never a wrong continent.

---

## What Was Built

### GPS warning strip on main screen (`activity_main.xml`, `MainActivity.kt`)
- Amber strip (`#F59E0B`) between header and controls card, `visibility="gone"` by default
- Shown in `onResume()` when `ACCESS_FINE_LOCATION` not granted; auto-hides when granted
- "FIX" button deep-links to app permission settings (`ACTION_APPLICATION_DETAILS_SETTINGS`)
- Driver sees it before shift starts, not after missing 10 towns

### Pill shows "📍 no GPS" (`AccessibilityOfferReceiver.kt`)
- Now checks `ACCESS_FINE_LOCATION` grant explicitly before calling `LocationHelper`
- If permission is the specific cause of `method == "unavailable"`, overrides `displayName` to `"📍 no GPS"` via `estimate.copy(displayName = "📍 no GPS")`
- Driver sees a meaningful signal mid-shift, not just `"📍 ---"`

### `resolveByCity` global search blocked (`DeliveryTownEstimator.kt`)
- Returns `null` immediately when `city == null` — no unconstrained Nominatim queries

---

## DebugOfferEmailer Investigation (unresolved)

Driver never received any debug emails. Diagnosis:
- `BuildConfig.DEBUG_SMTP_USER` / `DEBUG_SMTP_PASS` are correctly baked into the build ✓
- `BuildConfig.DEBUG = true` ✓
- No emailer logcat lines found (buffer rolled over)
- Logcat buffer was empty for GigLens by the time this was investigated

**Most likely causes (in order):**
1. Emails landing in Gmail spam — **check spam folder first**
2. SMTP port 587 blocked by cellular carrier — exception caught silently as `Log.w`
3. Gmail App Password may be in wrong format — should be 16 chars, no special characters

**How to diagnose next shift:**
```bash
adb logcat -s DebugOfferEmailer:*
```
Watch for `"Debug offer email sent"` or the failure exception message. If blocked by carrier, consider switching to port 465 (SSL) or a different SMTP relay.

**Status:** 🔲 Unresolved — check spam folder, then watch logcat during next offer.

---

## Files Changed This Session

| File | Change |
|------|--------|
| `geocoding/DeliveryTownEstimator.kt` | `resolveByCity` returns null when city=null; prevents global restaurant search |
| `service/AccessibilityOfferReceiver.kt` | Explicit permission check; overrides displayName to "📍 no GPS" when denied |
| `ui/MainActivity.kt` | GPS warning strip shown/hidden in onResume; FIX button wired |
| `res/layout/activity_main.xml` | Amber GPS warning strip added between header and controls card |

---

## ADB / Device Notes
- Port at session start: stale (34245 refused) → reconnected at 44993 → updated to 46119
- Current `.pixel_port`: `46119`
- Location permission: **granted** (via `adb shell pm grant` this session)
- DB data: IDs 1–3 from 2026-06-23 shift, all with null GPS — no usable town accuracy data

---

## Standing Issues

### townAccurate still null on all rows
Still no Yes/No taps on town confirmation notifications. Accuracy SQL still cannot run.

### Scoring redesign still not started
Phase 1 #1 priority. No code written across Sessions 3–5 (2026-06-22), this session, or the 2026-06-23 sessions. **Start here next session.**

### DebugOfferEmailer unvalidated
See above. Check spam, then watch logcat.

---

## Next Session — Start Here

1. **Check Gmail spam** for any missed debug offer emails.
2. **Top priority: Scoring redesign** — GREEN/YELLOW/RED configurable thresholds in Settings. Still completely unstarted.
3. **Verify GPS fix worked** — after next shift, pull DB and confirm `driverLat`/`driverLon` are non-null.
4. **Tap Yes/No** on town confirmation notifications during next shift.
