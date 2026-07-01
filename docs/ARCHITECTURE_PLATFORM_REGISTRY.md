# Architecture Direction — Platform Registry & Per-Platform Modularity

**Status:** Direction agreed 2026-06-30 (brainstorm). NOT yet implemented.
**Branch:** `feat/platform-registry`
**Drivers:** maintainability (each gig platform has different challenges) + privacy/trust.

> This is a design-direction note, not a spec. It records *decisions* so a future
> session doesn't re-litigate them. Implementation scope is deliberately small for now.

---

## The core idea

Different gig platforms (DoorDash, Uber Eats, Grubhub, Instacart, Amazon Flex) render
offers, maps, and pins completely differently — DoorDash is Mapbox dark theme with
briefcase/house pins and a specific notification banner; Uber Eats is Google Maps
(light), different pins, different accessibility tree; etc. Cramming every platform's
quirks into shared files is the opposite of maintainable.

So: make **"supported platform" a first-class concept** backed by an explicit
**registry**, and let platform-specific logic live in **per-platform modules**.

---

## Decisions (agreed)

1. **Per-platform modules, born when platform #2 is real — not before.**
   Extracting a `core` + `platform-*` split with only one platform risks drawing the
   shared/specific seam in the wrong place (you learn the real boundary from the second
   example). Near-term this is effectively **one platform module boundary**
   (`:app` + DoorDash logic), a **one-entry registry**, and a Settings surface. The
   full `core-analysis` + per-platform decomposition is deferred until Uber Eats (or
   whichever is second) actually forces us to name what's common.

2. **An explicit registry is the spine.** A known, enumerated set of supported
   platforms. Each entry binds: package name(s) → platform implementation (parser, map/
   pin analyzer, thresholds, banner handling) → presentation (display name, icon).
   **Single source of truth** for "what we support."

3. **Detect by package name at the accessibility layer — not by OCR text.**
   Today the platform is *guessed* from scanned text ("doordash"/"dasher" → DoorDash)
   *after* capture. The accessibility service already knows the foreground app's
   **package name** for free, before any OCR. Flow becomes:
   `window event → read foreground package → registry lookup → supported? activate that
   platform's pipeline : idle.` Package name is authoritative and cheap; it replaces
   content-sniffing.

4. **The registry drives the OS-level `packageNames` filter → structurally blind to
   everything else.** Set the accessibility service's `packageNames` from the registry
   (at runtime via `setServiceInfo`, or generated). Then **Android only ever delivers
   GigLens events from the supported gig apps** — the service is *incapable* of seeing
   texts, banking, browser, etc. This is the strong form of the privacy guarantee:
   "Android won't even hand us anything else," not "we receive everything and promise
   not to look."

5. **The same registry drives a Settings "supported apps" surface — DoorDash only.**
   Show the live registry list plus a plain reassurance: *"GigLens only reads offer
   screens in these apps. It never sees or records anything else — not your messages,
   not your other apps, not your activity. Everything is processed on your phone and
   nothing leaves it."* Because the registry feeds **both** the OS filter **and** the
   Settings display, what the driver reads is exactly what the code does — no gap
   between promise and behavior. Defensible to a driver *and* to Google Play review
   (which scrutinizes accessibility apps for stalkerware-style over-collection).

6. **Do NOT display future/coming-soon platforms.** Show only what's actually
   supported (today: DoorDash). A roadmap list would invite the exact worry we're
   killing ("are they going to watch all these apps?"). No `status`/`beta`/`coming
   soon` field — don't build it until there's a real second platform.

7. **Unsupported apps → no response (silent idle).** Enforced by #4.

---

## Why this matters beyond "easier to add Grubhub"

The same architectural decision serves **both** maintainability **and** trust:
the explicit, package-gated, registry-driven design is the foundation of a credible
"we don't monitor you" guarantee. This extends GigLens's existing ethos (zero-permission
default, on-device, nothing leaves the phone) to the *scary* accessibility permission —
a genuine competitive edge.

Dovetails with existing backlog items:
- **Accessibility Disclosure Screen** (shown before granting the permission) — same
  registry list + reassurance belongs there too, so the driver hears it *before* they
  grant accessibility and can re-confirm any time after.
- **Play Store accessibility justification** — the registry is the honest, auditable
  scope statement.

---

## Near-term scope (this branch)

- This design note.
- (When ready) the **registry** (one entry: DoorDash) as the single source of truth.
- The accessibility **package filter sourced from the registry** (likely already
  DoorDash-only — formalize it).
- A Settings **"supported apps"** section: DoorDash + the on-device / not-monitoring
  reassurance copy.

Explicitly **NOT** in near-term scope (deferred until platform #2):
- The `core-analysis` + `platform-*` module split.
- Abstracting `Bitmap`/`Color`/`PointF` for off-device testing (was "Option A" — only
  buys off-device unit testing, which is not a current goal; the app always runs on the
  phone).
- Any multi-platform machinery or roadmap/status UI.

---

## Open questions (not yet decided)

- **Per-app toggle** in Settings (driver disables a specific supported app, which also
  drops it from the OS filter) — strongest trust signal, but possibly overkill while
  only one app is supported. Revisit at platform #2.
- Whether the not-monitoring copy lives in **Settings only** or **both** Settings and
  the pre-permission disclosure screen. (Leaning: both.)
