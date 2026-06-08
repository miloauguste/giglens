#!/home/poppa/giglens/tools/venv/bin/python3
"""
gen_handover.py — GigLens Session Handover Generator
Author: Claude (Anthropic) | Auguste Enterprise
"""
import os, sys, datetime, subprocess, re

REPO_ROOT       = "/home/poppa/giglens"
TRANSCRIPT_FILE = f"{REPO_ROOT}/session_transcript.txt"
LAST_SESSION    = f"{REPO_ROOT}/docs/LAST_SESSION.md"
VERSION_FILE    = f"{REPO_ROOT}/version.txt"
DECISIONS_FILE  = f"{REPO_ROOT}/docs/DECISIONS.md"
ROADMAP_FILE    = f"{REPO_ROOT}/docs/FEATURE_ROADMAP.md"
MODEL           = "llama3.1:8b"
MAX_TOKENS      = 4000
OLLAMA_URL      = "http://localhost:11434/api/generate"

def read_file_safe(path, fallback="(not available)"):
    return open(path).read().strip() if os.path.exists(path) else fallback

def get_changed_files():
    # Show files changed in last commit + any uncommitted changes
    r1 = subprocess.run(["git","-C",REPO_ROOT,"diff","--name-only","HEAD~1","HEAD"], capture_output=True, text=True)
    r2 = subprocess.run(["git","-C",REPO_ROOT,"diff","--name-only","HEAD"], capture_output=True, text=True)
    r3 = subprocess.run(["git","-C",REPO_ROOT,"diff","--cached","--name-only"], capture_output=True, text=True)
    files = set(r1.stdout.strip().splitlines() + r2.stdout.strip().splitlines() + r3.stdout.strip().splitlines())
    return "\n".join(f"- {f}" for f in sorted(files)) if files else "- (no changes detected)"

def get_build_state():
    print("[handover] Checking build state...")
    r = subprocess.run(["bash","-c",f"cd {REPO_ROOT} && ./gradlew assembleDebug 2>&1 | tail -5"],
        capture_output=True, text=True, timeout=120)
    return "PASSING" if "BUILD SUCCESSFUL" in r.stdout else "FAILING"

def get_version():
    return read_file_safe(VERSION_FILE, fallback="unknown")

def build_system_prompt():
    existing = read_file_safe(LAST_SESSION)
    roadmap  = read_file_safe(ROADMAP_FILE)
    return f"""You are a technical project manager for GigLens, an Android app for gig economy drivers
built by Milo Auguste Jr. (Auguste Enterprise). Read the chat transcript and produce a
structured session handover document.

Output ONLY valid markdown filling in the sections below.
No preamble, no explanation, no code fences around the output.

Project: GigLens — Android (Kotlin, ML Kit OCR, MediaProjection, Room DB)
Build server: milo-dev at 10.0.0.16
Devices: Samsung S20 (10.0.0.189:5555), Pixel 10 XL (10.0.0.110:5555)
GitHub: github.com/miloauguste/giglens

## Current Roadmap
{roadmap}

## Previous Handover
{existing}

---

## What Was Completed This Session
[Every concrete thing built, fixed, or confirmed working. File and function names where mentioned.]

## What Was Left Incomplete
[Work started but not finished. Current state so next developer knows where to pick up.]

## Known Broken (do not ignore)
[Bugs or broken states. File, function, exact error for each.]

## Next Session — Start Here
**First task:** [Single most important thing — be specific]
**GitHub Issue:** #[N or "create new issue"]
**Context needed:**
[Critical context not obvious from code]

## Active Feature Status
| Feature | Status | Notes |
|---------|--------|-------|
[One row per active feature. Status: ✅ Complete | 🟡 In progress | 🔴 Broken/Blocked | ⬜ Not started]

## Decisions Made This Session
[- **[topic]:** [what was decided and why]]

---"""

def call_api(transcript, system_prompt):
    import requests
    print(f"[handover] Calling Ollama {MODEL} at {OLLAMA_URL}...")
    full_prompt = (
        f"{system_prompt}\n\n"
        f"Generate the LAST_SESSION.md handover from this transcript.\n\n"
        f"--- TRANSCRIPT START ---\n{transcript}\n--- TRANSCRIPT END ---"
    )
    try:
        response = requests.post(OLLAMA_URL, json={
            "model": MODEL,
            "prompt": full_prompt,
            "stream": False,
            "options": {
                "num_predict": MAX_TOKENS,
                "temperature": 0.2
            }
        }, timeout=300)
        response.raise_for_status()
        result = response.json()
        return result.get("response", "").strip()
    except requests.exceptions.ConnectionError:
        print(f"[handover] ERROR: Cannot connect to Ollama at {OLLAMA_URL}")
        print("           Make sure Ollama is running: ollama serve")
        sys.exit(1)
    except requests.exceptions.Timeout:
        print("[handover] ERROR: Ollama timed out after 300s")
        sys.exit(1)
    except Exception as e:
        print(f"[handover] ERROR: {e}")
        sys.exit(1)

def write_last_session(narrative, version, build_state, changed_files, date):
    # Strip any header the model may have generated — we write our own
    clean = narrative
    for prefix in ["# GigLens", "**Date:**", "**Version", "**Build", "**Conducted"]:
        if clean.startswith(prefix):
            lines = clean.splitlines()
            # Skip lines until we hit the first ## section
            for i, line in enumerate(lines):
                if line.startswith("## "):
                    clean = "\n".join(lines[i:])
                    break
            break
    content = f"""# GigLens — Session Handover
**Date:** {date}
**Version at session end:** {version}
**Build state:** {build_state}
**Conducted by:** Claude (auto-generated via tools/gen_handover.py — llama3.1:8b)

---

{clean}

## Devices & Build Environment

- Build server: milo-dev (i9, 48GB RAM, Ubuntu 24) at 10.0.0.16
- Pixel 10 XL (10.0.0.110:PORT): check Wireless Debugging each session — port changes on reboot

## Files Changed This Session

{changed_files}

---
*Auto-generated by tools/gen_handover.py — next developer read SESSION_PROTOCOL.md first.*
"""
    os.makedirs(os.path.dirname(LAST_SESSION), exist_ok=True)
    with open(LAST_SESSION, "w") as f:
        f.write(content)
    print("[handover] LAST_SESSION.md written.")

def append_decisions(narrative):
    m = re.search(r"## Decisions Made This Session\n(.*?)(?=\n## |\Z)", narrative, re.DOTALL)
    if not m: return
    text = m.group(1).strip()
    if not text or "none" in text.lower(): return
    entry = f"\n## {datetime.date.today().isoformat()} — Decisions from this session\n\n{text}\n"
    with open(DECISIONS_FILE, "a") as f:
        f.write(entry)
    print("[handover] Decisions appended to DECISIONS.md.")

def archive_transcript():
    if not os.path.exists(TRANSCRIPT_FILE): return
    archive_dir = f"{REPO_ROOT}/docs/transcripts"
    os.makedirs(archive_dir, exist_ok=True)
    dest = f"{archive_dir}/transcript_{datetime.date.today().isoformat()}.txt"
    os.rename(TRANSCRIPT_FILE, dest)
    print(f"[handover] Transcript archived to {dest}")

def main():
    print("\n=== GigLens Session Handover Generator ===\n")
    if not os.path.exists(TRANSCRIPT_FILE):
        print(f"[handover] ERROR: No transcript at {TRANSCRIPT_FILE}")
        print(f"           cat > {TRANSCRIPT_FILE}  then paste and Ctrl+D")
        sys.exit(1)
    transcript = read_file_safe(TRANSCRIPT_FILE)
    if len(transcript.strip()) < 100:
        print("[handover] ERROR: Transcript too short.")
        sys.exit(1)
    print(f"[handover] Transcript loaded ({len(transcript)} chars)")
    version       = get_version()
    changed_files = get_changed_files()
    date          = datetime.date.today().isoformat()
    build_state   = "SKIPPED" if "--no-build" in sys.argv else get_build_state()
    print(f"[handover] Version: {version} | Build: {build_state}")
    prompt    = build_system_prompt()
    narrative = call_api(transcript, prompt)
    write_last_session(narrative, version, build_state, changed_files, date)
    append_decisions(narrative)
    archive_transcript()
    print(f"\n✅ Handover complete.")
    print(f"   Review:  cat {LAST_SESSION}")
    print(f"   Commit:  cd {REPO_ROOT} && git add -A && git commit -m \'chore: session handover {date}\'")

if __name__ == "__main__":
    main()
