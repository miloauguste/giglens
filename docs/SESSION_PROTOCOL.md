# GigLens Session Protocol
<!-- Author: Claude (Anthropic) | Auguste Enterprise | Last updated: 2026-06-03 -->

This document is the **law of the land** for every GigLens development session.
Any AI assistant (Claude, DeepSeek, Gemini, etc.) or human developer MUST read
this file in full before writing a single line of code.

---

## Table of Contents

1. [First-Time Project Setup](#1-first-time-project-setup)
2. [Session Start Rules](#2-session-start-rules)
3. [Memory Management](#3-memory-management)
4. [Project Planning & Tracking](#4-project-planning--tracking)
5. [Session End & Handover Protocol](#5-session-end--handover-protocol)
6. [Standing Dev Rules (Never Break)](#6-standing-dev-rules-never-break)

---

## 1. First-Time Project Setup

Follow this checklist **once**, the very first time a new developer or AI agent
touches this project. Check each item before moving on.

### 1.1 Repository Orientation

```bash
# Clone the repo
git clone https://github.com/miloauguste/giglens.git
cd giglens

# Read these files IN THIS ORDER before doing anything else:
# 1. SESSION_PROTOCOL.md    ← you are here
# 2. CLAUDE.md              ← architecture, OCR rules, security practices
# 3. CHANGELOG.md           ← what has been built and when
# 4. docs/FEATURE_ROADMAP.md ← what is planned
```

### 1.2 Environment Verification

```bash
# Confirm you are on the correct machine
hostname  # expected: milo-dev

# Confirm Java / Android SDK
java -version          # must be 17+
echo $ANDROID_HOME     # must be set

# Confirm ADB devices are visible
adb devices
# Expected output includes:
#   10.0.0.189:5555   device   (Samsung S20)
#   10.0.0.110:5555   device   (Pixel 10 XL)

# Confirm the project builds cleanly before touching anything
cd /home/poppa/giglens
./gradlew assembleDebug 2>&1 | tail -10
# Must end with: BUILD SUCCESSFUL
```

### 1.3 Understand the Standing Rules

Before writing code, state back (in your first response) the following rules
to confirm you have read and understood them:

- All edits via `python3 << 'EOF'` heredoc scripts — no sed, no nano
- No hardcoded values in business logic — all config in DB tables
- Build must pass (`./gradlew assembleDebug`) before every commit
- Pre-commit hook auto-bumps version — never manually change version numbers
- CHANGELOG.md updated in the same commit as code — no exceptions
- QA first, prod only via `git pull` from main — never direct edits to prod

### 1.4 First-Time GitHub Setup (if repo not yet pushed)

```bash
cd /home/poppa/giglens
git remote add origin https://github.com/miloauguste/giglens.git
git branch -M main
git push -u origin main
```

---

## 2. Session Start Rules

Every session begins with this checklist, no exceptions.

### Step 1 — Read the Handover

```bash
cat /home/poppa/giglens/docs/LAST_SESSION.md
```

This file tells you exactly where the last session ended, what is broken,
and what to work on next. Do not rely on memory. Do not assume.

### Step 2 — Confirm Build State

```bash
cd /home/poppa/giglens
git pull
./gradlew assembleDebug 2>&1 | tail -10
```

**If BUILD FAILED:** Stop. Fix the build before doing anything else.
Report the error from `LAST_SESSION.md` under `## Known Broken`.

**If BUILD SUCCESSFUL:** Proceed.

### Step 3 — Confirm Devices

```bash
adb devices
```

If devices are offline:

```bash
adb connect 10.0.0.189:5555
adb connect 10.0.0.110:5555
adb devices
```

### Step 4 — State Your Session Plan

Before writing any code, output a short plan in this format:

```
SESSION PLAN — [date]
Working on: [feature or bug, with GitHub Issue # if applicable]
Current version: [read from version.txt]
Build state: PASSING / FAILING
Devices online: S20 / Pixel / both / none
First task: [one sentence]
```

---

## 3. Memory Management

AI assistants have no persistent memory between sessions. This section
defines how project knowledge is preserved and loaded.

### 3.1 What Lives in the Repo (Source of Truth)

| File | Purpose |
|------|---------|
| `SESSION_PROTOCOL.md` | Session rules (this file) |
| `CLAUDE.md` | Architecture, OCR rules, security practices, feature roadmap |
| `CHANGELOG.md` | Every change ever made, with version and date |
| `docs/LAST_SESSION.md` | Handover document from the most recent session |
| `docs/FEATURE_ROADMAP.md` | All planned features with status |
| `docs/DECISIONS.md` | UI and architecture decisions with rationale |

### 3.2 What NOT to Rely On

- **AI memory** — treat it as zero. Always read `LAST_SESSION.md` fresh.
- **Verbal agreements** — if it is not in a file, it does not exist.
- **Prior chat context** — a new chat window means a clean slate.

### 3.3 Decisions Log

Any time a decision is made (UI, architecture, behavior), it MUST be
logged in `docs/DECISIONS.md` in this format:

```markdown
## [date] — [decision title]
**Decision:** [what was decided]
**Rationale:** [why]
**Alternatives rejected:** [what else was considered and why it lost]
**Approved by:** Milo
```

Examples of decisions that must be logged:
- Choosing red rounded pill over stop-sign octagon for SKIP verdict
- Removing blink animation from countdown timer
- Setting 15-mile hard cap on delivery leg distance

---

## 4. Project Planning & Tracking

### 4.1 Feature Roadmap

All features live in `docs/FEATURE_ROADMAP.md`. Format:

```markdown
## Feature #N — [Name]
**Status:** [ ] Not started | [~] In progress | [x] Complete | [!] Blocked
**Priority:** HIGH / MEDIUM / LOW
**GitHub Issue:** #N
**Description:** [what it does and why]
**Acceptance criteria:**
- [ ] criterion 1
- [ ] criterion 2
**Notes:** [any partial work, blockers, or decisions]
```

### 4.2 GitHub Issues = Single Source of Task Truth

Every feature and bug gets a GitHub Issue. No exceptions.

Create issues at: https://github.com/miloauguste/giglens/issues/new

**Issue labels to use:**

| Label | Use for |
|-------|---------|
| `bug` | Something broken |
| `feature` | New capability |
| `security` | Security pipeline work |
| `ux` | UI/UX decisions |
| `blocked` | Cannot proceed — dependency or decision needed |
| `needs-mockup` | Must see visual before building |

Reference issues in every commit:

```bash
git commit -m "fix: prevent duplicate overlay views - closes #7"
```

### 4.3 Milestones

Milestones group issues into releases. Current milestones:

| Milestone | Focus |
|-----------|-------|
| v0.2.0 — Overlay Stability | Fix `isViewAdded` bug, overlay lifecycle |
| v0.3.0 — Scoring Engine | Tunable weights, IRS rate config via Settings UI |
| v1.0.0 — Beta Driver Release | Polished UI, onboarding, driver testing |

---

## 5. Session End & Handover Protocol

**This is mandatory at the end of every session.** If the session ends
without a handover document, the next session will be blind.

### 5.1 Generate the Handover

At session end, run:

```bash
cat /home/poppa/giglens/docs/LAST_SESSION.md
```

Then overwrite it with the new handover using the template below.
Use `python3 << 'EOF'` to write the file.

### 5.2 Handover Template

Save to: `/home/poppa/giglens/docs/LAST_SESSION.md`

```markdown
# GigLens — Session Handover
**Date:** YYYY-MM-DD
**Version at session end:** [read from version.txt]
**Build state:** PASSING / FAILING
**Conducted by:** [Claude / DeepSeek / Milo]

---

## What Was Completed This Session

- [bullet: what was built or fixed, with commit hash if available]
- [bullet]

## What Was Left Incomplete

- [bullet: work started but not finished, with current state]
- [bullet]

## Known Broken (do not ignore)

- [bullet: anything that is currently broken or will cause build failure]
  - File: [filename]
  - Line/function: [location]
  - Error: [exact error message or description]

## Next Session — Start Here

**First task:** [single most important thing to do, be specific]
**GitHub Issue:** #N
**Context needed:**
- [what the next developer needs to know that is not in code comments]

## Active Feature Status

| Feature | Status | Issue |
|---------|--------|-------|
| isViewAdded duplicate fix | In progress | #7 |
| OCR scoring tuning | Not started | #12 |
| Auto-capture Feature #8 | In progress | #8 |

## Devices & Build Environment

- S20 (10.0.0.189:5555): [ONLINE / OFFLINE]
- Pixel 10 XL (10.0.0.110:5555): [ONLINE / OFFLINE]
- Last successful install: [S20 / Pixel / both / neither]

## Files Changed This Session

[List of files modified — copy from `git diff --name-only HEAD~N HEAD`]

## Decisions Made This Session

[Any UI, architecture, or behavior decisions — copy to docs/DECISIONS.md also]
```

### 5.3 Commit the Handover

```bash
cd /home/poppa/giglens
git add docs/LAST_SESSION.md docs/DECISIONS.md docs/FEATURE_ROADMAP.md
git commit -m "chore: session handover [date] — [one-line summary of session]"
git push
```

---

## 6. Standing Dev Rules (Never Break)

These rules apply to every change in every session. If an AI assistant
violates any of these, Milo should correct it immediately.

### Code Changes

```
✅ All file edits via python3 << 'EOF' heredoc scripts
✅ No hardcoded values in business logic (all config in DB tables)
✅ ./gradlew assembleDebug must pass before every commit
✅ CHANGELOG.md updated in same commit as code
✅ Pre-commit hook auto-bumps version — never manually bump
✅ Code authorship header in every changed file (which LLM modified it)
✅ Install on both test devices after every build: S20 + Pixel 10 XL
```

### Git Discipline

```
✅ Feature branches for new work (feature/name, fix/name)
✅ Commit messages: type: description - closes #N
✅ Never commit directly to main from a feature branch without testing
✅ git pull before starting any session
✅ Push handover doc at end of every session
```

### UI/UX

```
✅ Mockup first — no UI implementation without Milo's visual approval
✅ No animations without explicit approval
✅ Settings changes via CRM/app UI — sqlite3 only if UI not yet built
```

### Security

```
✅ Run security_scan.sh at end of every session
✅ No API keys, tokens, or secrets in code — .env only
✅ MobSF baseline score: 46/100 (debug build — acceptable)
```

### DeepSeek-Specific Rules

```
✅ Every instruction must include a CORRECT example and a WRONG example
✅ Rules without examples will be ignored by DeepSeek
✅ Use !!MANDATORY OVERRIDE!! block elevation for critical constraints
```

---

*This document is version-controlled. Any changes to the protocol must be
committed with message: `chore: update SESSION_PROTOCOL.md — [reason]`*
