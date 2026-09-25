#!/usr/bin/env bash
# Black-box API checks against the deployed submission. Writes a JUnit-style summary to $RESULTS.
# Add project-specific checks per blueprint here (duplicate transactions, invalid input, auth, ...).
# The candidate never receives this file.
set -uo pipefail

BASE_URL="${BASE_URL:?}"
RESULTS="${RESULTS:?}"
total=0; passed=0; failed=(); 

check() {
  local name="$1"; shift
  total=$((total + 1))
  if "$@" >/dev/null 2>&1; then passed=$((passed + 1)); else failed+=("$name"); fi
}

check "health_is_up"            sh -c "curl -fsS '$BASE_URL/actuator/health' | grep -q '\"UP\"'"
check "unknown_route_returns_404" sh -c "test \"\$(curl -s -o /dev/null -w '%{http_code}' '$BASE_URL/__missing__')\" = 404"
check "malformed_json_is_rejected" sh -c "code=\$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{bad' '$BASE_URL/api/accounts'); test \"\$code\" -ge 400 -a \"\$code\" -lt 500"

printf '{"total":%d,"passed":%d,"failed":%d,"skipped":0,"failedTests":[%s]}\n' \
  "$total" "$passed" "${#failed[@]}" \
  "$(printf '"%s",' "${failed[@]:-}" | sed 's/,$//; s/^""$//')" > "$RESULTS"

[ "${#failed[@]}" -eq 0 ]
