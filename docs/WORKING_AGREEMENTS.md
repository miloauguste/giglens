# Working Agreements — GigLens Development

**Created:** 2026-06-17
**Purpose:** Standing rules that apply to EVERY session, regardless of what
changed in the last handover. Unlike LAST_SESSION.md (overwritten each session)
and FEATURE_BACKLOG.md (running list of features), this file should rarely
change — it captures how Milo and Claude work together, not what was built.

---

## Core Principle: Verify, Don't Assume

**The rule:** When a technical claim is uncertain, contested, or load-bearing
for a decision — search for current, authoritative information before stating
it as fact. Do not rely on training-data memory alone for anything where being
wrong has real cost (wasted build time, a feature that ships broken, a
decision made on bad information).

**Why this exists:** During the 2026-06-17 session, Claude stated confidently
that MediaProjection's consent dialog was unavoidable for ANY screen-capture
approach. This was wrong. A web search — prompted only because Milo kept
pushing back and asking "is there really no way?" — revealed
AccessibilityService.takeScreenshot(), a legitimate API that solves the exact
problem without MediaProjection at all. If Milo had accepted the first
confident-but-wrong answer, GigLens would have either shipped a worse solution
or abandoned a genuinely solvable feature.

**What this means in practice:**
- If Claude is about to say "this isn't possible" or "there's no way to do X" —
  that's exactly the moment to search first, not after being pushed.
- Milo pushing back ("I still think there's a way," "what about this
  alternative") is valuable signal, not friction to be managed. Treat it as a
  prompt to re-verify, not as something to politely deflect while repeating
  the same conclusion.
- It's fine to be wrong and corrected by search results. It is not fine to be
  confidently wrong and never check.
- When genuinely uncertain, say so explicitly ("I'm not certain this is
  correct, let me verify") rather than presenting a guess with unwarranted
  confidence.

---

## Decision Point Discipline

- Before writing significant code (especially anything touching architecture,
  a new external API, or a previously-failed approach), confirm the plan in
  plain language first. Milo has caught several cases where a misunderstanding
  would have led to wasted patches if not caught early (e.g. confusing
  "automatic mode" text-extraction with map-image-based estimation).
- When a feature investigation hits a dead end, document it clearly as CLOSED
  in the relevant handover/backlog doc, with the specific reasoning, so it
  isn't accidentally re-investigated in a future session without new
  information.
- Conversely: a topic being marked "closed" in a past handover does NOT mean
  it should be refused outright when revisited — see the takeScreenshot()
  example above. "Closed" means "don't blindly re-try the same failed
  approach," not "never reconsider this problem space again."

---

## Patch Script Hygiene

- Python heredoc patch scripts have repeatedly introduced a specific bug: `$`
  characters in Kotlin string templates getting escaped to `\$` when they
  shouldn't be, producing valid-but-wrong Kotlin (literal text instead of
  interpolated values). Before writing a patch script with Kotlin string
  templates inside a Python string, double-check dollar-sign handling
  specifically — this has caused real, hard-to-diagnose bugs at least twice.
- After any patch script reports success, don't assume the build is clean —
  always run a compile check immediately afterward as a separate, explicit
  step.
- If a build fails with cascading "unresolved reference" errors in a file that
  wasn't edited, don't assume the edited file is innocent — but also don't
  assume cache corruption first. Check git status/log on the failing file; an
  untracked, never-committed file is a strong signal of orphaned work from a
  prior session, not a fresh bug.

---

## Documentation Discipline

- FEATURE_BACKLOG.md gets updated as part of every handover, not just when
  something new is scoped — completed items move out, new decisions get
  recorded.
- LAST_SESSION.md should record not just what was built, but what was tried
  and failed, and why — future sessions (and future Claude instances with no
  memory of this conversation) need that context to avoid repeating dead ends
  OR avoid wrongly assuming something is permanently closed.
- This file (WORKING_AGREEMENTS.md) should be read at the start of every
  session alongside SESSION_PROTOCOL.md and LAST_SESSION.md.

---

*This file should be revised only when a real working pattern emerges worth
codifying — not as a dumping ground for one-off notes. Keep it short enough
that it actually gets read.*
