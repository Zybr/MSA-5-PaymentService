#!/usr/bin/env bash
set -euo pipefail

api_url="${API_URL:-http://localhost:8080}"

wait_for_status() {
  local payment_id="$1"
  local expected_status="$2"
  local response

  for _ in $(seq 1 60); do
    response="$(curl -fsS "$api_url/payments/$payment_id")"
    if printf '%s' "$response" | grep -q "\"status\":\"$expected_status\""; then
      echo "OK: payment=$payment_id status=$expected_status"
      return 0
    fi
    sleep 1
  done

  echo "FAILED: payment=$payment_id expected=$expected_status" >&2
  echo "$response" >&2
  return 1
}

start_payment() {
  local scenario="$1"
  local expected_status="$2"
  local extra_json="${3:-}"
  local response
  local payment_id

  response="$(curl -fsS -X POST "$api_url/payments" \
    -H 'Content-Type: application/json' \
    -d "{\"scenario\":\"$scenario\"$extra_json}")"
  payment_id="$(printf '%s' "$response" | sed -n 's/.*"paymentId":"\([^"]*\)".*/\1/p')"

  if [ -z "$payment_id" ]; then
    echo "FAILED: cannot read paymentId from $response" >&2
    return 1
  fi

  wait_for_status "$payment_id" "$expected_status"
}

curl -fsS "$api_url/health" | grep -q '"status":"UP"'
echo "OK: service health"

start_payment "success" "COMPLETED"
start_payment "debit-failure" "FAILED"
start_payment "fraud-deny" "REFUNDED"
start_payment "compliance-deny" "REFUNDED"
start_payment "limits-deny" "REFUNDED"
start_payment "manual-allow" "COMPLETED"
start_payment "manual-deny" "REFUNDED"
start_payment "cutoff" "COMPLETED" ',"cutOffDuration":"PT2S"'
start_payment "credit-failure" "REFUNDED"

echo "All payment scenarios passed."
