# GigLens — Session Start Prompt
Copy and paste everything below this line into Claude Code at the start of every session:

---

Read these files before we do anything:
- docs/LAST_SESSION_20260630.md
- docs/WORKING_AGREEMENTS.md  
- docs/FEATURE_BACKLOG.md

Then:
1. Summarize what was left incomplete from last session
2. State the top priority for this session
3. Confirm you understand all standing rules from WORKING_AGREEMENTS.md
4. Run: adb connect 10.0.0.110:$(cat ~/giglens/.pixel_port)

Do not write any code until you have done all four steps above.
