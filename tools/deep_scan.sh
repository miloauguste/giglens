#!/bin/bash
# deep_scan.sh — GigLens Full Security Deep Scan
# Author: Claude (Anthropic) | Auguste Enterprise
# Runs all security tools independently of git
#
# Usage:
#   bash /home/poppa/giglens/tools/deep_scan.sh           # full scan
#   bash /home/poppa/giglens/tools/deep_scan.sh --quick   # skip OWASP + MobSF
#   bash /home/poppa/giglens/tools/deep_scan.sh --mobsf   # MobSF only
#   bash /home/poppa/giglens/tools/deep_scan.sh --owasp   # OWASP only

GIGLENS_DIR="/home/poppa/giglens"
VENV_PA="/home/poppa/project_autonomous/venv"
VENV_TH="/home/poppa/project_autonomous/venv_trufflehog"
AAPT="/home/poppa/android-sdk/build-tools/34.0.0/aapt"
OWASP="/home/poppa/tools/dependency-check/bin/dependency-check.sh"
APK="$GIGLENS_DIR/app/build/outputs/apk/debug/app-debug.apk"
MOBSF_URL="http://localhost:8010"
MOBSF_APIKEY="8dae5ab1941af7ef1a1e3923a1049c54dc4c8f7d7e1f0fd7afdee69bcead8d4c"
REPORT_DIR="$GIGLENS_DIR/docs/security-reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$REPORT_DIR/deep_scan_$TIMESTAMP.txt"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

FAIL=0
WARNINGS=0
QUICK=0
MOBSF_ONLY=0
OWASP_ONLY=0

for arg in "$@"; do
    case $arg in
        --quick) QUICK=1 ;;
        --mobsf) MOBSF_ONLY=1 ;;
        --owasp) OWASP_ONLY=1 ;;
    esac
done

mkdir -p "$REPORT_DIR"

# Tee all output to report file
exec > >(tee "$REPORT_FILE") 2>&1

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║         GigLens Deep Security Scan                   ║${NC}"
echo -e "${BOLD}║         $(date +"%Y-%m-%d %H:%M:%S")                        ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# ── 0. Build fresh APK ────────────────────────────────────────────────────────
if [ "$MOBSF_ONLY" != "1" ] && [ "$OWASP_ONLY" != "1" ]; then
    echo -e "${YELLOW}[Build]${NC} Building fresh debug APK..."
    if cd "$GIGLENS_DIR" && ./gradlew assembleDebug --quiet 2>/dev/null; then
        echo -e "${GREEN}[OK]${NC} APK built: $APK"
    else
        echo -e "${RED}[FAIL]${NC} Build failed — scan may be incomplete"
        FAIL=1
    fi
fi

# ── 1. Gitleaks — full repo secrets scan ──────────────────────────────────────
if [ "$MOBSF_ONLY" != "1" ] && [ "$OWASP_ONLY" != "1" ]; then
    echo ""
    echo -e "${YELLOW}[Gitleaks]${NC} Full repo secrets scan..."
    if gitleaks detect         --source="$GIGLENS_DIR"         --no-git         --config="$GIGLENS_DIR/.gitleaks.toml"         --report-format=json         --report-path="$REPORT_DIR/gitleaks_$TIMESTAMP.json"         2>/dev/null; then
        echo -e "${GREEN}[OK]${NC} No secrets detected"
    else
        LEAK_COUNT=$(python3 -c "import json; d=json.load(open('$REPORT_DIR/gitleaks_$TIMESTAMP.json')); print(len(d))" 2>/dev/null || echo "?")
        echo -e "${RED}[FAIL]${NC} $LEAK_COUNT secret(s) found — see $REPORT_DIR/gitleaks_$TIMESTAMP.json"
        FAIL=1
    fi

    # ── 2. Gitleaks — git history scan ────────────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Gitleaks]${NC} Git commit history scan..."
    if gitleaks detect         --source="$GIGLENS_DIR"         --config="$GIGLENS_DIR/.gitleaks.toml"         --report-format=json         --report-path="$REPORT_DIR/gitleaks_history_$TIMESTAMP.json"         2>/dev/null; then
        echo -e "${GREEN}[OK]${NC} No secrets in git history"
    else
        echo -e "${RED}[FAIL]${NC} Secrets found in git history — review and rotate keys"
        FAIL=1
    fi

    # ── 3. Trufflehog — entropy + pattern scan ────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Trufflehog]${NC} Entropy and pattern scan..."
    source "$VENV_TH/bin/activate"
    if trufflehog filesystem "$GIGLENS_DIR/app/src"         --only-verified         --json         --no-update         2>/dev/null | tee "$REPORT_DIR/trufflehog_$TIMESTAMP.json" | python3 -c "
import sys, json
findings = [json.loads(l) for l in sys.stdin if l.strip()]
print(len(findings))
" | grep -q "^0$"; then
        echo -e "${GREEN}[OK]${NC} No verified secrets"
    else
        echo -e "${RED}[FAIL]${NC} Verified secrets found — see $REPORT_DIR/trufflehog_$TIMESTAMP.json"
        FAIL=1
    fi
    deactivate

    # ── 4. Semgrep — full ruleset ─────────────────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Semgrep]${NC} Full static analysis (all severities)..."
    source "$VENV_PA/bin/activate"
    SEMGREP_OUT=$(semgrep         --config=auto         --include="*.kt"         --json         "$GIGLENS_DIR/app/src/" 2>/dev/null)
    deactivate

    ERROR_COUNT=$(echo "$SEMGREP_OUT" | python3 -c "
import sys,json
d=json.load(sys.stdin)
results=d.get('results',[])
errors=[r for r in results if r.get('extra',{}).get('severity') in ['ERROR','WARNING']]
print(len(errors))
" 2>/dev/null || echo 0)

    echo "$SEMGREP_OUT" > "$REPORT_DIR/semgrep_$TIMESTAMP.json"

    if [ "$ERROR_COUNT" -gt "0" ]; then
        echo -e "${YELLOW}[WARN]${NC} Semgrep: $ERROR_COUNT findings — see $REPORT_DIR/semgrep_$TIMESTAMP.json"
        WARNINGS=$((WARNINGS + 1))
    else
        echo -e "${GREEN}[OK]${NC} Semgrep clean"
    fi

    # ── 5. Android Lint ───────────────────────────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Lint]${NC} Android Lint full report..."
    if cd "$GIGLENS_DIR" && ./gradlew :app:lint --quiet 2>/dev/null; then
        ERROR_COUNT=$(grep -c 'severity="Error"' app/build/reports/lint-results-debug.xml 2>/dev/null || echo 0)
        WARN_COUNT=$(grep -c 'severity="Warning"' app/build/reports/lint-results-debug.xml 2>/dev/null || echo 0)
        if [ "$ERROR_COUNT" -gt "0" ]; then
            echo -e "${RED}[FAIL]${NC} Lint: $ERROR_COUNT errors, $WARN_COUNT warnings"
            FAIL=1
        else
            echo -e "${GREEN}[OK]${NC} Lint: 0 errors, $WARN_COUNT warnings"
        fi
    fi

    # ── 6. Hardcoded values check ─────────────────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Hardcoded]${NC} Scanning for hardcoded config values..."
    HARDCODED=$(grep -rn         --include="*.kt"         -E "("[A-Z][a-z]+ [A-Z][a-z]+, USA"|api_key|apiKey|password|secret|localhost)"         "$GIGLENS_DIR/app/src/" 2>/dev/null | grep -v "^\s*//" | grep -v "BuildConfig" || true)
    if [ -n "$HARDCODED" ]; then
        echo -e "${YELLOW}[WARN]${NC} Possible hardcoded values:"
        echo "$HARDCODED"
        WARNINGS=$((WARNINGS + 1))
    else
        echo -e "${GREEN}[OK]${NC} No hardcoded values detected"
    fi

    # ── 7. APK permission audit ───────────────────────────────────────────────
    echo ""
    echo -e "${YELLOW}[Permissions]${NC} APK permission audit..."
    if [ -f "$APK" ] && [ -f "$AAPT" ]; then
        ACTUAL=$("$AAPT" dump permissions "$APK" 2>/dev/null |             grep "uses-permission" |             grep -o "name='[^']*'" |             sed "s/name='//;s/'//")
        PERM_COUNT=$(echo "$ACTUAL" | grep -c "." 2>/dev/null || echo 0)
        echo -e "${GREEN}[OK]${NC} $PERM_COUNT permissions declared:"
        echo "$ACTUAL" | sed "s/^/    /"
    else
        echo -e "${CYAN}[SKIP]${NC} APK not found — run assembleDebug first"
    fi
fi

# ── 8. OWASP dependency check ─────────────────────────────────────────────────
if [ "$QUICK" != "1" ] && [ "$MOBSF_ONLY" != "1" ]; then
    echo ""
    echo -e "${YELLOW}[OWASP]${NC} Dependency CVE scan (this takes 2-5 mins)..."
    mkdir -p "$GIGLENS_DIR/app/build/reports/owasp"
    "$OWASP"         --project "GigLens-DeepScan"         --scan "$GIGLENS_DIR/app/build/outputs/apk/debug/app-debug.apk"         --scan "$GIGLENS_DIR/app/build.gradle.kts"         --out "$GIGLENS_DIR/app/build/reports/owasp"         --format HTML         --format JSON         --nvdApiKey "6a504b43-11fe-4ffe-aa4e-ef4ee028c31c"         --failOnCVSS 7         --suppression "$GIGLENS_DIR/.owasp-suppressions.xml"         > /tmp/owasp_deep.txt 2>&1
    OWASP_EXIT=$?
    if grep -q "403 or 404" /tmp/owasp_deep.txt 2>/dev/null; then
        echo -e "${CYAN}[SKIP]${NC} NVD API key not yet active"
    elif [ "$OWASP_EXIT" != "0" ]; then
        echo -e "${RED}[FAIL]${NC} HIGH/CRITICAL CVEs — review app/build/reports/owasp/"
        FAIL=1
    else
        echo -e "${GREEN}[OK]${NC} No HIGH/CRITICAL CVEs — report: app/build/reports/owasp/"
    fi
fi

# ── 9. MobSF full static analysis ────────────────────────────────────────────
if [ "$QUICK" != "1" ] && [ "$OWASP_ONLY" != "1" ]; then
    echo ""
    echo -e "${YELLOW}[MobSF]${NC} Full static APK analysis..."
    # Start MobSF container if not running
    if ! curl -s "$MOBSF_URL" > /dev/null 2>&1; then
        echo -e "${CYAN}[INFO]${NC} Starting MobSF Docker container..."
        docker start mobsf > /dev/null 2>&1
        echo -e "${CYAN}[INFO]${NC} Waiting for MobSF to be ready..."
        for i in $(seq 1 24); do
            sleep 5
            if curl -s "$MOBSF_URL" > /dev/null 2>&1; then
                echo -e "${GREEN}[OK]${NC} MobSF ready"
                break
            fi
            echo -e "${CYAN}[INFO]${NC} Still waiting... ($((i*5))s)"
        done
        if ! curl -s "$MOBSF_URL" > /dev/null 2>&1; then
            echo -e "${RED}[FAIL]${NC} MobSF failed to start after 120s — skipping"
        fi
    fi

    if [ -f "$APK" ] && curl -s "$MOBSF_URL" > /dev/null 2>&1; then
        UPLOAD=$(curl -s -F "file=@$APK"             -H "Authorization: $MOBSF_APIKEY"             "$MOBSF_URL/api/v1/upload" 2>/dev/null)
        HASH=$(echo "$UPLOAD" | python3 -c             "import sys,json; print(json.load(sys.stdin).get('hash',''))" 2>/dev/null)
        if [ -n "$HASH" ]; then
            echo -e "${CYAN}[INFO]${NC} Scanning APK hash: $HASH"
            curl -s -X POST                 -H "Authorization: $MOBSF_APIKEY"                 --data "hash=$HASH"                 "$MOBSF_URL/api/v1/scan" > /dev/null 2>&1
            sleep 3
            SCORECARD=$(curl -s -X POST                 -H "Authorization: $MOBSF_APIKEY"                 --data "hash=$HASH"                 "$MOBSF_URL/api/v1/scorecard" 2>/dev/null)
            SCORE=$(echo "$SCORECARD" | python3 -c                 "import sys,json; d=json.load(sys.stdin); print(d.get('security_score','?'))"                 2>/dev/null || echo "?")
            echo "$SCORECARD" > "$REPORT_DIR/mobsf_scorecard_$TIMESTAMP.json"

            # Download PDF report
            curl -s -X POST                 -H "Authorization: $MOBSF_APIKEY"                 --data "hash=$HASH"                 "$MOBSF_URL/api/v1/download_pdf"                 -o "$REPORT_DIR/mobsf_report_$TIMESTAMP.pdf" 2>/dev/null
            echo -e "${GREEN}[OK]${NC} MobSF score: $SCORE/100"
            echo -e "${CYAN}[INFO]${NC} PDF report: $REPORT_DIR/mobsf_report_$TIMESTAMP.pdf"
            echo -e "${CYAN}[INFO]${NC} Full UI: $MOBSF_URL"
            if [ "${SCORE:-100}" -lt "40" ] 2>/dev/null; then
                echo -e "${RED}[FAIL]${NC} Score below 40 threshold for debug builds"
                FAIL=1
            fi
        else
            echo -e "${CYAN}[SKIP]${NC} MobSF upload failed"
        fi
    else
        echo -e "${CYAN}[SKIP]${NC} MobSF not reachable — start with: docker start mobsf"
    fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║                  Scan Summary                        ║${NC}"
echo -e "${BOLD}╠══════════════════════════════════════════════════════╣${NC}"
if [ "$FAIL" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo -e "${BOLD}║${NC}  ${GREEN}✅ CLEAN — no issues found${NC}                          ${BOLD}║${NC}"
elif [ "$FAIL" -eq 0 ]; then
    echo -e "${BOLD}║${NC}  ${YELLOW}⚠️  WARNINGS: $WARNINGS item(s) to review${NC}                 ${BOLD}║${NC}"
else
    echo -e "${BOLD}║${NC}  ${RED}❌ FAILED — $FAIL blocker(s) found${NC}                    ${BOLD}║${NC}"
fi
echo -e "${BOLD}║${NC}  Reports saved to: docs/security-reports/           ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  Full report: $REPORT_FILE ${BOLD}║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

exit $FAIL
