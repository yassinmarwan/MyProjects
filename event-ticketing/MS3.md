# Milestone 3 — Event Ticketing Platform

> **Due:** Saturday 17/05/2026 at 11:59 PM | **Weight:** 40% of final grade

---

## Overview

MS3 transforms the system from a shared-database monolith into true microservices with:
- **Database isolation** — each service owns its own PostgreSQL instance
- **Synchronous inter-service communication** via OpenFeign
- **Asynchronous event-driven choreography** via RabbitMQ
- **Spring Cloud Gateway** as unified entry point
- **Kubernetes deployment** on MiniKube

---

## Services & Databases

| Service | Port | Isolated PostgreSQL DB |
|---|---|---|
| user-service | 8080 | `etdb-users` |
| event-service | 8080 | `etdb-events` |
| booking-service | 8080 | `etdb-bookings` |
| ticket-service | 8080 | `etdb-tickets` |
| sales-service | 8080 | `etdb-sales` |
| api-gateway | 8080 | None |

NoSQL (MongoDB, Redis, Elasticsearch, Neo4j, Cassandra) remain shared with logical isolation.

---

## Architecture Principles

### Database Isolation (§1)
- No cross-database JDBC connections
- Cross-service references become plain `Long` fields (no JPA `@ManyToOne`)
- All cross-service data fetched via Feign at runtime

### Inter-Service Communication (§2)
- **Feign (sync):** HTTP calls with error handling; failures must not cascade
- **RabbitMQ (async):** Topic exchanges; at-least-once delivery; all consumers must be **idempotent**
- **`X-Correlation-ID`** header propagates on every Feign call
- Dead-letter queues capture failed messages after 3 retry attempts (1000ms initial interval)

---

## Choreography Saga — Booking Completion (§8)

**Trigger:** `PUT /api/bookings/{id}/complete`

### Forward Path
1. S3 validates 3 Feign pre-checks (event status, user status, used-ticket count)
2. S3 publishes `booking.completed`
3. S1, S2, S4, S5 consume and update local state
4. S5 creates PENDING TicketSale → publishes `payment.initiated`
5. S3 consumes `payment.initiated` → booking transitions to `PAYMENT_PENDING`
6. Customer calls `POST /api/sales/booking/{id}` with payment details
7. Payment succeeds → `payment.completed` → booking becomes `PAID`

### Compensation Path (payment failure)
1. S5 publishes `payment.failed`
2. S3 consumes → publishes `booking.cancelled`
3. S1 reverses user stats; S2 reverses event stats
4. S4 cancels all VALID tickets → publishes `ticket.cancelled` per ticket
5. S5 refunds the TicketSale → publishes `payment.refunded`
6. Booking final state: `REFUNDED`

### New Booking Status Values
`COMPLETING` → `PAYMENT_PENDING` → `PAID` / `PAYMENT_FAILED` → `REFUNDED`

---

## RabbitMQ Full Event Map

### Exchanges

| Exchange | Type | Declared By |
|---|---|---|
| `user.events` | TopicExchange | user-service |
| `event.events` | TopicExchange | event-service |
| `booking.events` | TopicExchange | booking-service |
| `ticket.events` | TopicExchange | ticket-service |
| `payment.events` | TopicExchange | sales-service |

### All Events

| Exchange | Routing Key | Publisher | Payload | Consumers |
|---|---|---|---|---|
| `user.events` | `user.registered` | S1 | `{userId, email, role}` | S3 |
| `user.events` | `user.deactivated` | S1 | `{userId}` | S3 |
| `event.events` | `event.status-changed` | S2 | `{eventId, oldStatus, newStatus}` | S3, S4 |
| `event.events` | `event.rated` | S2 | `{eventId, bookingId, rating, userId}` | S3 |
| `booking.events` | `booking.placed` | S3 | `{bookingId, userId, eventId}` | S2, S4 |
| `booking.events` | `booking.completed` | S3 | `{bookingId, userId, eventId, totalAmount}` | S1, S2, S4, S5 |
| `booking.events` | `booking.cancelled` | S3 | `{bookingId, userId, eventId, reason}` | S1, S2, S4, S5 |
| `ticket.events` | `ticket.issued` | S4 | `{ticketId, bookingId, attendeeName, ticketCode}` | S3 |
| `ticket.events` | `ticket.status-changed` | S4 | `{ticketId, bookingId, newStatus}` | S3 |
| `ticket.events` | `ticket.cancelled` | S4 | `{ticketId, bookingId}` | S3 |
| `payment.events` | `payment.initiated` | S5 | `{saleId, bookingId, amount}` | S3 |
| `payment.events` | `payment.completed` | S5 | `{saleId, bookingId, amount}` | S3 |
| `payment.events` | `payment.failed` | S5 | `{saleId, bookingId, reason}` | S3 |
| `payment.events` | `payment.refunded` | S5 | `{saleId, bookingId, refundAmount}` | S3 |

### Consumer Queues

| Service | Queue | DLQ | Binds To |
|---|---|---|---|
| user-service | `user.booking.saga-listener` | `user.booking.saga-listener.dlq` | `booking.events` (completed, cancelled) |
| event-service | `event.booking.saga-listener` | `event.booking.saga-listener.dlq` | `booking.events` (placed, completed, cancelled) |
| booking-service | `booking.saga-feedback` | `booking.saga-feedback.dlq` | `ticket.events` (issued); `payment.events` (all) |
| ticket-service | `ticket.booking.saga-listener` | `ticket.booking.saga-listener.dlq` | `booking.events` (placed, completed, cancelled) |
| sales-service | `payment.saga-listener` | `payment.saga-listener.dlq` | `booking.events` (completed, cancelled) |

### RabbitMQ Connection Config (all services)

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```

---

## Ticket Service (S4) — MS3 Tasks

Student **55-1937** owns the ticket-service.

### Database Changes

| Change | Details |
|---|---|
| Add column `eventId BIGINT` to `tickets` | Denormalization strategy — avoids cross-DB joins at read time |
| Populate `eventId` on `booking.placed` consumption | Capture from event payload for future tickets on this booking |
| Populate `eventId` at issue time | Via Feign call to booking-service (get booking → eventId) |

### New Endpoints to Expose

| Method | Path | Returns | Purpose |
|---|---|---|---|
| `GET` | `/api/tickets/booking/{bookingId}/used-count` | `int` | Saga pre-check for S3-F4 |
| `GET` | `/api/tickets/event/{eventId}/summary` | `EventTicketSummaryDTO` | Called by S2-F12 dashboard |

`EventTicketSummaryDTO`: `{ totalTicketsSold, usedCount }`

### Feign Clients Required

```yaml
# application.yml
feign:
  booking-service:
    url: http://booking-service:8080
  event-service:
    url: http://event-service:8080
```

**BookingServiceClient:**
- `GET /api/bookings/{id}` → `BookingDTO`

**EventServiceClient:**
- `GET /api/events/{id}` → `EventDTO`
- `GET /api/events/{id}/venue-coords` → `VenueCoordsDTO`

Wrap every Feign call in try-catch — never let a downstream failure crash the ticket-service.

### Feature-by-Feature Changes

#### S4-F1 — Get Latest Ticket for Booking
- **Before:** Direct cross-DB query to booking-postgres
- **After:** Feign → booking-service `GET /api/bookings/{bookingId}`
- If Feign returns 404 → throw 404 from ticket-service

#### S4-F2 — Issue Ticket
- **Before:** Local booking existence check
- **After:** Feign → booking-service to validate; populate denormalized `eventId` from response
- Publish `ticket.issued` after save: `{ticketId, bookingId, attendeeName, ticketCode}`

#### S4-F3 — Find Nearby Tickets
- **Before:** Hardcoded or local coordinates
- **After:** Two-stage Feign chain:
  1. Feign → booking-service to get `eventId` for each ticket
  2. Feign → event-service `GET /api/events/{eventId}/venue-coords` for each unique eventId
  3. Compute: `distanceKm = sqrt((lat2-lat1)² + (lon2-lon1)²) × 111`
  4. Filter by radiusKm, sort ascending
- Cache `eventId → (lat, lon, name)` within request scope to minimize Feign calls
- Returns: `List<NearbyTicketDTO> {ticketId, attendeeName, bookingId, eventName, eventLat, eventLon, distanceKm}`

#### S4-F4 — Batch Ticket Issuance
- **Before:** Local booking validation
- **After:** Feign → booking-service to validate; populate `eventId`; publish one `ticket.issued` per ticket (or batch event)

#### S4-F5 — Filter Tickets by Metadata
- **No MS3 change** — pure local JSONB query

#### S4-F6 — Get Tickets in Date Range
- **No MS3 change** — local query on `issuedAt`

#### S4-F7 — Purge Expired Tickets
- **No MS3 change** — local DELETE

#### S4-F8 — Event Attendance Summary
- **Before:** Cross-DB query or Cassandra
- **After:** Use denormalized `eventId` column — local query only
- SQL: `SELECT COUNT(*) as totalTickets, COUNT(CASE WHEN status='USED' THEN 1 END) as usedCount FROM tickets WHERE eventId=?`
- Returns both `EventTicketSummaryDTO {totalTicketsSold, usedCount}` and legacy `EventAttendanceSummaryDTO {eventId, totalTickets, usedTickets, validTickets, attendanceRate, lastCheckIn}`
- 404 if no tickets found for eventId

#### S4-F9 — Unused Upcoming Tickets
- **Before:** Local query on event dates
- **After:** Use denormalized `eventId`; Feign → event-service `GET /api/events/{eventId}` to check `status=UPCOMING`
- Filter out events that are not UPCOMING; return remaining tickets

### RabbitMQ — Ticket Service

**Publisher — `ticket.events` TopicExchange:**

| Event | When | Payload |
|---|---|---|
| `ticket.issued` | After S4-F2 / S4-F4 save | `{ticketId, bookingId, attendeeName, ticketCode}` |
| `ticket.status-changed` | Status update (audit signal) | `{ticketId, bookingId, newStatus}` |
| `ticket.cancelled` | After `booking.cancelled` consumption | `{ticketId, bookingId}` |

**Consumer — queue `ticket.booking.saga-listener` (DLQ: `ticket.booking.saga-listener.dlq`):**

| Routing Key | Source | Action |
|---|---|---|
| `booking.placed` | `booking.events` | Capture `eventId` for denormalization; store mapping `bookingId → eventId` |
| `booking.completed` | `booking.events` | Log saga progression; optionally publish `ticket.status-changed` per ticket (audit only, no status mutation) |
| `booking.cancelled` | `booking.events` | Set all VALID/USED tickets for booking to CANCELLED; publish `ticket.cancelled` per ticket |

### S4 Deliverables Checklist

- [ ] Add `Long eventId` column — Flyway migration
- [ ] `application.yml` — isolated `etdb-tickets` datasource + Feign URLs
- [ ] `BookingServiceClient` — `getBooking(Long id)`
- [ ] `EventServiceClient` — `getEvent(Long id)`, `getEventVenueCoords(Long id)`
- [ ] Endpoint: `GET /api/tickets/booking/{bookingId}/used-count` → `int`
- [ ] Endpoint: `GET /api/tickets/event/{eventId}/summary` → `EventTicketSummaryDTO`
- [ ] S4-F1 — Feign booking validation
- [ ] S4-F2 — Feign booking + denormalized eventId + publish `ticket.issued`
- [ ] S4-F3 — Two-stage Feign chain + distance calculation
- [ ] S4-F4 — Feign booking + batch publish `ticket.issued`
- [ ] S4-F8 — Local query using denormalized eventId (no Feign at read time)
- [ ] S4-F9 — denormalized eventId + Feign event-service for status filtering
- [ ] Declare `ticket.events` TopicExchange
- [ ] Declare consumer queue `ticket.booking.saga-listener` with DLQ
- [ ] Consumer: `booking.placed` → capture eventId
- [ ] Consumer: `booking.completed` → log + optional audit signal
- [ ] Consumer: `booking.cancelled` → cancel tickets + publish `ticket.cancelled`
- [ ] Wrap all Feign calls in try-catch (no cascade failures)
- [ ] Propagate `X-Correlation-ID` on all outgoing Feign calls
- [ ] `logback-spring.xml` with Loki4J appender

---

## All Services — Feign Call Reference

### ticket-service calls
- `booking-service` → `GET /api/bookings/{id}`
- `event-service` → `GET /api/events/{id}`
- `event-service` → `GET /api/events/{id}/venue-coords`

### booking-service calls (for saga context)
- `event-service` → `GET /api/events/{id}`, `GET /api/events/{id}/sessions/avg-capacity`, `GET /api/events/{id}/venue-coords`
- `user-service` → `GET /api/users/{id}`
- `ticket-service` → `GET /api/tickets/booking/{bookingId}/used-count`

### event-service calls
- `booking-service` → `GET /api/bookings/event/{eventId}/revenue`, `GET /api/bookings/event/{eventId}/active-count`, `GET /api/bookings/{bookingId}`
- `user-service` → `GET /api/users/{id}`
- `ticket-service` → `GET /api/tickets/event/{eventId}/summary`

### user-service calls
- `booking-service` → `GET /api/bookings/user/{userId}/summary`, `GET /api/bookings/user/{userId}/active-count`

### sales-service calls
- `user-service` → `GET /api/users/{id}`
- `booking-service` → `GET /api/bookings/{id}`, `GET /api/bookings/{bookingId}/items`
- `event-service` → `GET /api/events/{id}`

---

## API Gateway

- Spring Cloud Gateway (reactive WebFlux)
- JWT validation as global filter
- Forwards `X-User-Id`, `X-User-Role`, `X-Correlation-ID` headers to downstream services
- Routes by path: `/api/users/**`, `/api/events/**`, `/api/bookings/**`, `/api/tickets/**`, `/api/sales/**`
- Exposed on NodePort `30080`

---

## Kubernetes Deployment

- StatefulSets for all databases (each PostgreSQL instance has its own PersistentVolume)
- Deployments for all 5 services + API Gateway
- ClusterIP Services for inter-service calls; NodePort for API Gateway
- Namespace isolation
- Secrets for credentials; ConfigMaps for `application.yml` properties
- Shared StatefulSets for: RabbitMQ, MongoDB, Redis, Elasticsearch, Neo4j, Cassandra

---

## Observability

- `logback-spring.xml` with **Loki4J** appender in every service
- `X-Correlation-ID` included in all log entries for distributed tracing

---

## Test Scenarios (S4)

| Scenario | Setup | Action | Expected |
|---|---|---|---|
| S4-F1 happy path | 3 tickets for bookingId=1, different `issuedAt` | `GET /api/tickets/booking/1/latest` | 200 — most recent ticket |
| S4-F1 booking not found | Feign returns 404 for bookingId=999 | `GET /api/tickets/booking/999/latest` | 404 |
| S4-F2 issue ticket | Booking 1 exists | `POST /api/tickets/booking/1` | 201 + `ticket.issued` published |
| S4-F2 booking not found | Feign returns 404 for bookingId=999 | `POST /api/tickets/booking/999` | 404 |
| S4-F3 nearby filter | 3 events at coords; 6 tickets | `GET /api/tickets/nearby?lat=30.04&lon=31.23&radiusKm=5` | 200, sorted by distance asc |
| S4-F4 batch issue | Booking 1 exists | `POST /api/tickets/batch` with 3 tickets | 201 with count=3 |
| S4-F4 duplicate code | Duplicate ticketCode in batch | same endpoint | 400 |
| S4-F8 summary | 10 tickets for eventId=5 (6 USED, 3 VALID, 1 EXPIRED) | `GET /api/tickets/event/5/summary` | 200 with correct counts |
| S4-F8 not found | No tickets for eventId=999 | `GET /api/tickets/event/999/summary` | 404 |
| Saga pre-check | Booking 10 has 0 USED tickets | `PUT /api/bookings/10/complete` | 400 — booking stays CHECKED_IN |
