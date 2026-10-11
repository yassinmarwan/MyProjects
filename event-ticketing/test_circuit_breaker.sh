#!/usr/bin/env bash
# Circuit Breaker E2E Test
# Demonstrates: CLOSED → OPEN (fallback) → HALF_OPEN → CLOSED (recovery)
#
# Config (user-service application.yml):
#   sliding-window-size: 5
#   failure-rate-threshold: 50%  → opens after 3+ failures in 5 calls
#   wait-duration-in-open-state: 10s
#   permitted-number-of-calls-in-half-open-state: 2

set -euo pipefail

USER_URL="http://localhost:8081"
BOOKING_CONTAINER="booking-service"
REDIS_CONTAINER="eventticketing-redis"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RESET='\033[0m'

pass() { echo -e "${GREEN}  ✓ $*${RESET}"; }
fail() { echo -e "${RED}  ✗ $*${RESET}"; exit 1; }
info() { echo -e "${CYAN}$*${RESET}"; }
warn() { echo -e "${YELLOW}$*${RESET}"; }

# ── Helpers ───────────────────────────────────────────────────────────────────

flush_cache() {
  docker exec "$REDIS_CONTAINER" redis-cli -a redispass FLUSHALL > /dev/null 2>&1
}

get_token() {
  local token
  token=$(curl -s -X POST "$USER_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"ahmed@example.com","password":"password123"}' \
    | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  [[ -z "$token" ]] && { fail "Login failed — is user-service running and seeded?"; }
  echo "$token"
}

booking_summary() {
  local token=$1
  curl -s "$USER_URL/api/users/1/booking-summary" \
    -H "Authorization: Bearer $token"
}

total_bookings() {
  echo "$1" | grep -o '"totalBookings":[0-9]*' | cut -d: -f2
}

total_spent() {
  echo "$1" | grep -o '"totalSpent":[0-9.]*' | cut -d: -f2
}

assert_real_data() {
  local body=$1 label=$2
  local tb ts
  tb=$(total_bookings "$body")
  ts=$(total_spent "$body")
  [[ "$tb" == "5" && "$ts" == "1500.0" ]] \
    && pass "$label → totalBookings=$tb totalSpent=$ts (real data)" \
    || fail "$label — expected totalBookings=5 totalSpent=1500.0, got totalBookings=$tb totalSpent=$ts"
}

assert_fallback_data() {
  local body=$1 label=$2
  local tb ts
  tb=$(total_bookings "$body")
  ts=$(total_spent "$body")
  [[ "$tb" == "0" && "$ts" == "0.0" ]] \
    && pass "$label → totalBookings=$tb totalSpent=$ts (fallback)" \
    || fail "$label — expected fallback zeros, got totalBookings=$tb totalSpent=$ts"
}

# ── Pre-flight ────────────────────────────────────────────────────────────────

echo ""
info "═══════════════════════════════════════════════════════"
info "  Circuit Breaker E2E Test"
info "═══════════════════════════════════════════════════════"
echo ""

info "[ Pre-flight ] Flushing Redis cache and obtaining JWT..."
flush_cache
TOKEN=$(get_token)
pass "Logged in as ahmed@example.com"

# ── Phase 1: Circuit CLOSED — normal operation ────────────────────────────────

echo ""
info "[ Phase 1 ] Circuit CLOSED — booking-service is UP"

BODY=$(booking_summary "$TOKEN")
assert_real_data "$BODY" "Call 1"

# ── Phase 2: Stop booking-service, trip the circuit ───────────────────────────

echo ""
info "[ Phase 2 ] Stopping booking-service..."
docker stop "$BOOKING_CONTAINER" > /dev/null
pass "booking-service stopped"

echo ""
info "[ Phase 3 ] Circuit CLOSED→OPEN — firing 6 calls (window=5, threshold=50%)"
warn "  Calls will hit booking-service (timeout/connection refused) → fallback activates"

flush_cache
TOKEN=$(get_token)

for i in 1 2 3 4 5 6; do
  BODY=$(booking_summary "$TOKEN")
  assert_fallback_data "$BODY" "Call $i"
done

echo ""
info "  Checking user-service logs for BookingServiceFallback WARN..."
WARN_COUNT=$(docker logs user-service 2>&1 | grep -c "BookingServiceFallback.*getUserBookingSummary" || true)
[[ "$WARN_COUNT" -gt 0 ]] \
  && pass "BookingServiceFallback logged $WARN_COUNT WARN(s)" \
  || fail "No BookingServiceFallback WARN found in user-service logs"

# ── Phase 3: Restore booking-service, wait for HALF_OPEN ─────────────────────

echo ""
info "[ Phase 4 ] Restarting booking-service..."
docker start "$BOOKING_CONTAINER" > /dev/null
START_RFC=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
DEADLINE=$(( $(date +%s) + 90 ))

info "  Waiting for booking-service to be ready (max 90s)..."
while true; do
  if docker logs "$BOOKING_CONTAINER" --since "$START_RFC" 2>&1 | grep -q "Started BookingServiceApplication"; then
    break
  fi
  if [[ $(date +%s) -gt $DEADLINE ]]; then
    fail "booking-service did not start within 60s"
  fi
  sleep 2
done
pass "booking-service is up"

echo ""
info "[ Phase 5 ] Waiting 15s for circuit OPEN → HALF_OPEN and booking-service warmup..."
sleep 15
pass "Wait complete"

# ── Phase 4: Recovery ─────────────────────────────────────────────────────────

echo ""
info "[ Phase 6 ] Circuit HALF_OPEN → CLOSED — verifying recovery"
flush_cache
TOKEN=$(get_token)

# First probe — HALF_OPEN lets it through
BODY=$(booking_summary "$TOKEN")
assert_real_data "$BODY" "Recovery call 1 (HALF_OPEN probe)"

flush_cache
TOKEN=$(get_token)

# Second probe — should close the circuit
BODY=$(booking_summary "$TOKEN")
assert_real_data "$BODY" "Recovery call 2 (circuit CLOSED)"

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
info "═══════════════════════════════════════════════════════"
pass "All assertions passed — circuit breaker is working correctly"
info "  CLOSED  → normal Feign calls, real data"
info "  OPEN    → fallback immediately, zeros returned, WARN logged"
info "  HALF_OPEN → probe succeeds, circuit closes, real data restored"
info "═══════════════════════════════════════════════════════"
echo ""
