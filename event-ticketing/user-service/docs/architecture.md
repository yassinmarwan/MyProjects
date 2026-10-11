# Architecture

This document explains how the user-service is structured internally and how it fits into the wider platform.

---

## The four layers

Every request that comes into user-service travels through four layers. Think of it like a restaurant:

```
HTTP Request
     │
     ▼
┌─────────────┐
│  Controller │  ← The waiter. Takes your order (request), hands it to the kitchen,
│             │    brings back the result (response). No cooking happens here.
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Service   │  ← The chef. All the business logic lives here.
│             │    Decides what to do, calls the repository, calls other services.
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Repository  │  ← The pantry manager. Knows how to read and write to the database.
│             │    Spring Data JPA generates most of this automatically.
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Database   │  ← PostgreSQL. Where the actual data lives.
│ (PostgreSQL)│
└─────────────┘
```

### Controllers
Controllers live in the `controller/` package. They receive HTTP requests, call the right service method, and return an HTTP response. They contain no business logic — just routing and response formatting.

- `userController` — handles `/api/users/**`
- `AuthController` — handles `/api/auth/register` and `/api/auth/login`
- `UserReportController` — handles `/api/users/reports/**`
- `FavoriteVenueController` — handles `/api/favorite-venues/**`

### Services
Services live in the `service/` package. This is where the real work happens.

- `UserService` — user CRUD, search, preferences, deactivation, booking summary, top attendees, category filter
- `AuthService` — registration and login
- `FavoriteVenueService` — favorite venue CRUD and setting a default venue
- `JwtService` — creates and reads JWT tokens
- `CacheEvictionService` — clears Redis cache entries when data changes

### Repositories
Repositories live in the `repository/` package. They talk to the database.

- `UserRepository` — reads and writes the `users` table
- `FavoriteVenueRepository` — reads and writes the `favorite_venues` table
- `AuthEventRepository` — reads activity logs from MongoDB

### Entities
Entities live in the `entity/` package. They are Java objects that map to database rows.

- `User` — maps to the `users` table
- `FavoriteVenue` — maps to the `favorite_venues` table
- `Role` — enum: `ATTENDEE` or `ADMIN`
- `UserStatus` — enum: `ACTIVE` or `DEACTIVATED`

---

## Package map

```
com.team22.eventticketing.user/
│
├── controller/     HTTP endpoint handlers
├── service/        Business logic
├── repository/     Database access
├── entity/         JPA database models
├── dto/            Data shapes for API responses (never the raw entity)
├── config/         Spring configuration classes (security, Redis, RabbitMQ, etc.)
├── security/       JWT filter and auth handler chain
├── messaging/      RabbitMQ publisher and consumer
├── observer/       Event logging to MongoDB
├── mongo/          MongoDB document models
└── adapter/        Helper classes to convert between data formats
```

---

## Cross-service communication (Feign clients)

User-service needs data from booking-service for several features. Instead of sharing a database, it makes HTTP calls to booking-service. These calls are written as a Java interface called a **Feign client** — it looks like a normal method call in code, but under the hood it fires an HTTP request.

```
user-service                          booking-service
─────────────────────────────────     ─────────────────────────────
GET /api/users/1/booking-summary
  │
  └─► BookingServiceClient
        .getUserBookingSummary(1)  ──► GET /api/bookings/user/1/summary
                                   ◄── { totalBookings, totalSpent, ... }
```

The Feign client interface (`feign.BookingServiceClient` in the `contracts` module) declares all the available calls. You never write the HTTP code yourself — Feign generates it.

**Why this matters:** user-service never touches the bookings database directly. If booking-service is down, those endpoints will return an error, but the rest of user-service still works fine.

**What happens if booking-service goes down?** Rather than returning an error or hanging for a long time, user-service uses a **circuit breaker**. After a few failed calls it stops trying to reach booking-service and immediately returns safe default values (zeros for counts and amounts) until booking-service comes back. See [circuit-breaker.md](circuit-breaker.md) for the full details.

---

## Observer pattern (audit logging)

Every time something important happens (a user registers, updates their profile, deactivates their account, etc.), the service notifies a list of "observers". Right now there is one observer: `MongoEventLogger`.

```
UserService.deactivateUser()
  │
  └─► notifyObservers("USER_DEACTIVATED", { userId: 1 })
        │
        └─► MongoEventLogger.onEvent(...)
              │
              └─► writes to MongoDB collection: auth_events
```

This pattern keeps the logging code completely separate from the business logic. To add a new type of observer (e.g., send an email), you just implement the `EntityObserver` interface and register it — you don't touch `UserService` at all.

---

## RabbitMQ messaging

User-service both **publishes** events (tells other services what happened) and **consumes** events (listens for things other services did).

```
PUBLISHING (user-service → others)

  Registration  ──► [user.events exchange]  ──► routing key: user.registered
  Deactivation  ──► [user.events exchange]  ──► routing key: user.deactivated


CONSUMING (booking-service → user-service)

  [booking.events exchange]
      │
      ├── routing key: booking.completed ──► user.booking.saga-listener queue
      │                                         │
      └── routing key: booking.cancelled ──►    └─► BookingEventConsumer
                                                      updates user stats
```

See [messaging.md](messaging.md) for full details.

---

## Security filter chain

Every request (except login/register/health) passes through a chain of security checks. Each step does one thing and passes to the next. If any step fails, the request is rejected immediately.

```
Incoming request
      │
      ▼
TokenExtractionHandler
  "Does the Authorization header exist and start with Bearer?"
      │ yes
      ▼
SignatureValidationHandler
  "Is the JWT token valid and signed correctly?"
      │ yes
      ▼
UserLoaderHandler
  "Does this user still exist in the database?"
      │ yes
      ▼
RoleAuthorizationHandler
  "Does this user have the right role for this endpoint?"
      │ yes
      ▼
Request proceeds to controller
```

See [authentication.md](authentication.md) for more detail.
