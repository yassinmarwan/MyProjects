# Circuit Breaker

This document explains how user-service protects itself when booking-service is unavailable.

---

## The problem it solves

User-service calls booking-service over HTTP for several features — booking summaries, top attendees, deactivation checks, and more. If booking-service goes down, every one of those HTTP calls will hang for several seconds waiting for a response that never comes. Under load, this can exhaust all available threads and bring down user-service too, even though user-service itself is perfectly healthy.

A **circuit breaker** prevents this. Think of it like the circuit breaker in your home's fuse box: when something goes wrong and current spikes, the breaker trips and cuts the circuit immediately rather than letting the wiring burn.

---

## How it works (the three states)

The circuit breaker has three states. It moves between them automatically based on how calls are going.

```
          too many failures
CLOSED ────────────────────► OPEN
  ▲                            │
  │   probes succeed           │ wait 10s
  │                            ▼
HALF-OPEN ◄─────────────── OPEN
  │
  │ probe fails → back to OPEN
```

### CLOSED (normal operation)
Everything is fine. Calls go straight through to booking-service. The circuit breaker watches each call and keeps a count of failures.

### OPEN (booking-service is down)
Too many calls failed. The circuit breaker **stops calling booking-service entirely** and immediately returns a fallback response instead. No waiting, no timeouts — the user gets a response in milliseconds.

The circuit stays open for **10 seconds**, then moves to HALF-OPEN to check if booking-service has recovered.

### HALF-OPEN (checking if it recovered)
The circuit breaker lets **2 test calls** through to booking-service.
- If both succeed → circuit closes, normal operation resumes.
- If either fails → circuit opens again for another 10 seconds.

---

## What triggers the circuit to open

The circuit opens when **50% or more of the last 5 calls fail**. A call counts as a failure if:
- booking-service is unreachable (connection refused)
- booking-service takes longer than **5 seconds** to respond (timeout)
- booking-service throws an exception

So in practice: if 3 out of 5 recent calls fail, the circuit opens.

---

## What happens when the circuit is open (fallbacks)

Instead of returning an error to the user, user-service returns safe default values. This is called a **fallback**.

| Endpoint | Normal response | Fallback response |
|----------|----------------|-------------------|
| `GET /api/users/{id}/booking-summary` | Real booking totals from booking-service | All counts and amounts set to zero |
| `GET /api/users/top-attendees` | Real spending data per user | Each user shown with zero spending (filtered out of results) |
| `GET /api/users/category` | Filters by booking count | Falls back to zero — users may appear even if they don't meet the minimum booking count |
| `DELETE /api/users/{id}/deactivate` | Checks for active bookings | Assumes zero active bookings — deactivation is allowed to proceed |

The user-service logs a `WARN` every time a fallback fires, so you can see in the logs when booking-service is having trouble:

```
WARN BookingServiceFallback - booking-service circuit open — getUserBookingSummary for user 42: ...
```

---

## JWT propagation across threads

This is a subtle implementation detail worth understanding.

Normally, the JWT token from the incoming HTTP request is stored in a `ThreadLocal` called `AuthTokenHolder`. A ThreadLocal is like a variable that is private to a single thread — each thread has its own copy.

The problem: Resilience4j runs the circuit breaker supplier on a **different thread**. That thread has no ThreadLocal value, so the JWT would be lost — and the Feign call to booking-service would fail with 401 Unauthorized.

The fix: before handing off to the circuit breaker, we capture the token from the current thread and explicitly pass it into the new thread:

```
Request thread                        Circuit breaker thread
──────────────                        ──────────────────────
JWT arrives in request
AuthTokenHolder.set(token)            ← token captured before handoff
      │
      └─► cbRun(supplier, fallback)
              │
              └─► bookingCB.run(
                    () -> {
                      AuthTokenHolder.set(token)  ← re-set on CB thread
                      return bookingServiceClient.call()
                    },
                    fallback
                  )
```

This is handled in the private `cbRun()` helper in `UserService`. You don't need to do anything special when calling booking-service — just use `cbRun()` instead of calling the Feign client directly.

---

## Configuration reference

These settings live in `application.yml` under the `resilience4j` key:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      bookingService:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 5          # look at the last 5 calls
        failure-rate-threshold: 50      # open if 50%+ failed (3 of 5)
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 2
        register-health-indicator: true # shows up in /actuator/health
  timelimiter:
    instances:
      bookingService:
        timeout-duration: 5s            # a call taking longer than 5s counts as failure
```

The name `bookingService` must match exactly in both `circuitbreaker.instances` and `timelimiter.instances`, and must match the name passed to `circuitBreakerFactory.create("bookingService")` in `UserService`.

---

## Checking circuit breaker health

If Spring Actuator is exposed, you can check the current state of the circuit breaker:

```bash
GET /actuator/health
```

Look for `circuitBreakers.bookingService.details.state` in the response — it will be `CLOSED`, `OPEN`, or `HALF_OPEN`.

---

## Adding a new call to booking-service

If you add a new Feign call to `BookingServiceClient`, always wrap it in `cbRun()`:

```java
// ✅ Correct — goes through the circuit breaker
MyDTO result = cbRun(
    () -> bookingServiceClient.myNewCall(userId),
    t  -> fallback.myNewCallFallback(userId, t)
);

// ❌ Wrong — bypasses the circuit breaker entirely
MyDTO result = bookingServiceClient.myNewCall(userId);
```

And add a matching fallback method to `BookingServiceFallback` that returns a safe default value.
