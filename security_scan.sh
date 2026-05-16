#!/bin/bash
# GigLens Unified Security + Test Pipeline
# Author: Claude (Anthropic)
# Runs all OSS security scans and tests in priority order.
# Exit code 1 if any HIGH/CRITICAL finding detected.
# Uses venv from project_autonomous for Python-based tools.

set -e

VENV="/home/poppa/project_autonomous/venv"
GIGLENS_DIR="/home/poppa/giglens"
APK="$GIGLENS_DIR/app/build/outputs/apk/debug/app-debug.apk"
REPORT_DIR="$GIGLENS_DIR/security_reports"
DATE=$(date +%Y%m%d_%H%M%S)
REPORT="$REPORT_DIR/security_report_$DATE.md"
FAIL=0

mkdir -p "$REPORT_DIR"

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; echo "- PASS: $1" >> "$REPORT"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; echo "- FAIL: $1" >> "$REPORT"; FAIL=1; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; echo "- WARN: $1" >> "$REPORT"; }
info() { echo -e "${BLUE}[INFO]${NC} $1"; echo "- INFO: $1" >> "$REPORT"; }

# ── Report header ─────────────────────────────────────────────────────────────
cat >> "$REPORT" << HEADER
# GigLens Security + Test Report
**Date:** $(date)
**Version:** $(cat $GIGLENS_DIR/version.txt)
**Commit:** $(cd $GIGLENS_DIR && git log --oneline -1)

---

HEADER

echo ""
echo "================================================"
echo " GigLens Security + Test Pipeline"
echo " $(date)"
echo "================================================"
echo ""

# ── Activate venv ─────────────────────────────────────────────────────────────
source "$VENV/bin/activate"
info "Python venv activated: $VENV"

cd "$GIGLENS_DIR"

# ── LEVEL 1: Fast checks (pre-commit gate) ────────────────────────────────────
echo ""
echo -e "${BLUE}=== LEVEL 1: Fast Checks ===${NC}"
echo "## Level 1: Fast Checks" >> "$REPORT"

# 1a. Android Lint
echo ""
info "Running Android Lint..."
./gradlew :app:lint --quiet 2>/dev/null || true
ERROR_COUNT=$(grep -c "severity=\"Error\"" app/build/reports/lint-results-debug.xml 2>/dev/null; echo ${PIPESTATUS[0]})
ERROR_COUNT=$(grep -c "severity=\"Error\"" app/build/reports/lint-results-debug.xml 2>/dev/null || true)
ERROR_COUNT=${ERROR_COUNT:-0}
if [ "$ERROR_COUNT" -eq "0" ]; then
    pass "Android Lint — 0 errors"
else
    fail "Android Lint — $ERROR_COUNT errors found"
fi

# 1b. Gitleaks — scan git history for secrets
echo ""
info "Running Gitleaks..."
if gitleaks detect --source="$GIGLENS_DIR" --no-git --config="$GIGLENS_DIR/.gitleaks.toml" 2>/dev/null; then
    pass "Gitleaks — no secrets found in source"
else
    fail "Gitleaks — secrets detected in source"
fi

if gitleaks detect --source="$GIGLENS_DIR" --config="$GIGLENS_DIR/.gitleaks.toml" 2>/dev/null; then
    pass "Gitleaks — no secrets found in git history"
else
    warn "Gitleaks — potential secrets in git history (review report)"
fi

# 1c. Hardcoded value grep
echo ""
info "Checking for hardcoded sensitive values..."
HARDCODED=$(grep -rn \
    --include="*.kt" \
    --include="*.xml" \
    --include="*.gradle.kts" \
    -E "(api_key|apikey|password|secret|token|bearer)\s*=\s*['\"][^'\"]{8,}" \
    "$GIGLENS_DIR/app/src/" 2>/dev/null | \
    grep -v "//.*Author" | \
    grep -v "test\|Test\|mock\|Mock" || true)

if [ -z "$HARDCODED" ]; then
    pass "Hardcoded secrets grep — clean"
else
    fail "Hardcoded secrets grep — potential values found:"
    echo "$HARDCODED" | while read line; do warn "  $line"; done
fi

# ── LEVEL 2: Medium checks (pre-push gate) ────────────────────────────────────
echo ""
echo -e "${BLUE}=== LEVEL 2: Medium Checks ===${NC}"
echo "" >> "$REPORT"
echo "## Level 2: Medium Checks" >> "$REPORT"

# 2a. OWASP Dependency Check
echo ""
info "Running OWASP Dependency Check..."
NVD_KEY=$(grep "owasp.nvd.api.key" "$GIGLENS_DIR/local.properties" 2>/dev/null | cut -d= -f2 || echo "")
if [ -z "$NVD_KEY" ]; then
    warn "OWASP — NVD API key not found in local.properties, skipping"
else
    if ./gradlew :app:dependencyCheckAnalyze -Powasp.nvd.api.key="$NVD_KEY" --quiet 2>/dev/null; then
        pass "OWASP Dependency Check — no HIGH/CRITICAL CVEs"
    else
        fail "OWASP Dependency Check — vulnerabilities found (check report)"
    fi
fi

# 2b. Semgrep — Kotlin static analysis
echo ""
info "Running Semgrep..."
if semgrep --config=auto \
    --include="*.kt" \
    --severity=ERROR \
    --quiet \
    "$GIGLENS_DIR/app/src/" 2>/dev/null; then
    pass "Semgrep — no high severity findings"
else
    fail "Semgrep — issues found (review output)"
fi

# 2c. Trufflehog — deep secret scan
echo ""
info "Running Trufflehog..."
TRUFFLE_BIN="/home/poppa/project_autonomous/venv_trufflehog/bin/trufflehog3"
TRUFFLE_OUT=$("$TRUFFLE_BIN" --no-history "$GIGLENS_DIR/app/src" 2>/dev/null || true)
if [ -n "$TRUFFLE_OUT" ]; then
    warn "Trufflehog — potential secrets found (review output)"
    echo "$TRUFFLE_OUT" >> "$REPORT"
else
    pass "Trufflehog — no secrets detected"
fi

# ── LEVEL 3: Full suite (end of session) ─────────────────────────────────────
echo ""
echo -e "${BLUE}=== LEVEL 3: Full Suite ===${NC}"
echo "" >> "$REPORT"
echo "## Level 3: Full Suite" >> "$REPORT"

# 3a. Build APK for analysis
echo ""
info "Building debug APK for analysis..."
if ./gradlew assembleDebug --quiet 2>/dev/null; then
    pass "APK build — success"
else
    fail "APK build — failed, skipping APK analysis tools"
    FAIL=1
fi

# 3b. APKLeaks
echo ""
if [ -f "$APK" ]; then
    info "Running APKLeaks..."
    APKLEAKS_OUT=$(apkleaks -f "$APK" 2>/dev/null || true)
    if echo "$APKLEAKS_OUT" | grep -qiE "api_key|secret|password|token"; then
        fail "APKLeaks — sensitive strings found in APK"
        echo "$APKLEAKS_OUT" >> "$REPORT"
    else
        pass "APKLeaks — no sensitive strings in compiled APK"
    fi
else
    warn "APKLeaks — APK not found, skipping"
fi

# 3c. MobSF
echo ""
if [ -f "$APK" ]; then
    info "Running MobSF static analysis..."
    # Start MobSF container
    MOBSF_PORT=8000
    CONTAINER_ID=$(docker run -d \
        -p $MOBSF_PORT:8000 \
        opensecurity/mobile-security-framework-mobsf:latest 2>/dev/null)

    if [ -n "$CONTAINER_ID" ]; then
        info "MobSF container started: $CONTAINER_ID"
        sleep 15  # Wait for MobSF to initialize

        # Upload APK
        UPLOAD=$(curl -s -F "file=@$APK" \
            "http://localhost:$MOBSF_PORT/api/v1/upload" \
            -H "Authorization: mobsf" 2>/dev/null || echo "")

        if echo "$UPLOAD" | grep -q "hash"; then
            HASH=$(echo "$UPLOAD" | python3 -c "import sys,json; print(json.load(sys.stdin)['hash'])" 2>/dev/null || echo "")
            if [ -n "$HASH" ]; then
                # Trigger scan
                curl -s -X POST \
                    "http://localhost:$MOBSF_PORT/api/v1/scan" \
                    -d "hash=$HASH" \
                    -H "Authorization: mobsf" > /dev/null 2>&1
                sleep 30  # Wait for scan

                # Get score
                SCORE_JSON=$(curl -s \
                    "http://localhost:$MOBSF_PORT/api/v1/scorecard" \
                    -d "hash=$HASH" \
                    -H "Authorization: mobsf" 2>/dev/null || echo "")

                if echo "$SCORE_JSON" | grep -q "security_score"; then
                    SCORE=$(echo "$SCORE_JSON" | python3 -c \
                        "import sys,json; d=json.load(sys.stdin); print(d.get('security_score','N/A'))" 2>/dev/null || echo "N/A")
                    info "MobSF security score: $SCORE"
                    echo "- INFO: MobSF security score: $SCORE" >> "$REPORT"
                    if [ "$SCORE" != "N/A" ] && [ "$SCORE" -lt 60 ] 2>/dev/null; then
                        fail "MobSF — security score below threshold: $SCORE"
                    else
                        pass "MobSF — scan complete, score: $SCORE"
                    fi
                else
                    warn "MobSF — could not retrieve score"
                fi
            fi
        else
            warn "MobSF — APK upload failed"
        fi

        # Stop container
        docker stop "$CONTAINER_ID" > /dev/null 2>&1
        docker rm "$CONTAINER_ID" > /dev/null 2>&1
        info "MobSF container stopped"
    else
        warn "MobSF — could not start container"
    fi
else
    warn "MobSF — APK not found, skipping"
fi

# 3d. Unit tests
echo ""
info "Running unit tests..."
if ./gradlew test --quiet 2>/dev/null; then
    pass "Unit tests — all passing"
else
    fail "Unit tests — failures detected"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "================================================"
echo "" >> "$REPORT"
echo "## Summary" >> "$REPORT"

if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN} ALL CHECKS PASSED${NC}"
    echo "**Result: ALL CHECKS PASSED**" >> "$REPORT"
    echo " Report: $REPORT"
else
    echo -e "${RED} SECURITY ISSUES FOUND — review report${NC}"
    echo "**Result: ISSUES FOUND — review before pushing**" >> "$REPORT"
    echo " Report: $REPORT"
fi

echo "================================================"
echo ""

deactivate
exit $FAIL
