# Caching

User-service uses **Redis** to cache frequently-requested data. This document explains what gets cached, for how long, and when the cache is cleared.

---

## Why caching?

Some data is expensive to fetch — for example, the "top attendees" report requires calling booking-service for every user in the database. If 50 requests come in within a minute asking for the same report with the same date range, we don't want to make those calls 50 times.

Instead, we compute the result once, store it in Redis, and return the stored result for subsequent requests within the cache window.

**Think of it like:** writing the answer to a problem on a sticky note so the next person who asks gets an instant answer instead of having to solve it again.

---

## Cache names and lifetimes

| Cache name | TTL | What it stores | Cache key example |
|------------|-----|----------------|-------------------|
| `user-service::user` | 15 min | A single `User` object by ID | `user-service::user::1` |
| `user-service::favorite-venue` | 15 min | A single `FavoriteVenue` object by ID | `user-service::favorite-venue::10` |
| `user-service::S1-F1` | 5 min | User search results | `user-service::S1-F1::ahmed:null:null` |
| `user-service::S1-F3` | 10 min | Booking summary for a user | `user-service::S1-F3::1` |
| `user-service::S1-F5` | 5 min | Users filtered by a preference key-value | `user-service::S1-F5::language:en` |
| `user-service::S1-F6` | 10 min | Top attendees report | `user-service::S1-F6::2026-03-01:2026-03-31:10` |
| `user-service::S1-F8` | 15 min | User profile (includes favorite venues) | `user-service::S1-F8::1` |
| `user-service::S1-F9` | 10 min | Users filtered by favorite category + min bookings | `user-service::S1-F9::CONCERT:3` |
| `user-service::S1-F12` | 5 min | User activity feed (paginated) | `user-service::S1-F12::1::0::10` |

**TTL** means "time to live" — after this many minutes, the cached entry is automatically deleted. The next request after expiry will fetch fresh data and cache it again.

---

## When is the cache cleared early?

Waiting for the TTL to expire is fine for read-only data, but if a user updates their profile, we should return the new data immediately — not the cached old data.

This is called **cache eviction**. Whenever data changes, the service explicitly deletes the affected cache entries so the next request fetches fresh data.

### What triggers eviction

| Action | Caches cleared |
|--------|---------------|
| User updated (`PUT /api/users/{id}`) | `user`, `S1-F1`, `S1-F3`, `S1-F5`, `S1-F8`, `S1-F9` |
| User preferences updated | `user`, `S1-F1`, `S1-F3`, `S1-F5`, `S1-F8`, `S1-F9` |
| User deleted | `user`, `S1-F1`, `S1-F3`, `S1-F5`, `S1-F6`, `S1-F8`, `S1-F9` |
| User deactivated | `user`, `S1-F1`, `S1-F3`, `S1-F5`, `S1-F6`, `S1-F8`, `S1-F9` |
| User role changed | `user`, `S1-F12` (activity feed) |
| Favorite venue updated | `favorite-venue`, `S1-F8`, `S1-F9` |
| Favorite venue deleted | `favorite-venue`, `S1-F8`, `S1-F9` |
| Default venue set | `favorite-venue`, `user`, `S1-F8`, `S1-F9` |

### Why so many caches cleared on update?

Different features may return the same user data in different shapes. For example:
- `S1-F1` (search) returns a list of users — if the user's name changed, this list is stale
- `S1-F8` (profile) returns the user with their venues — same problem
- `S1-F9` (category filter) also returns user objects

Rather than tracking exactly which caches contain which user, the service takes the safe approach: when user data changes, clear everything that could possibly contain that user.

---

## Cache key format

All cache keys follow this pattern:
```
user-service::{cacheId}::{parameters}
```

For caches that store a single entity, the parameter is the entity's ID:
```
user-service::user::1
user-service::favorite-venue::42
```

For feature caches, the parameters are the query inputs joined by colons:
```
user-service::S1-F1::ahmed:null:ATTENDEE
user-service::S1-F6::2026-03-01:2026-03-31:10
user-service::S1-F12::1::0::10
```

This means two requests with different parameters get their own separate cache entries. Searching for "ahmed" and searching for "sara" are cached independently.

---

## What is NOT cached

The following data is never cached (always fetched fresh from the database):

- `GET /api/users` (full list of users)
- `GET /api/favorite-venues` (full list of venues)
- `GET /api/favorite-venues?userId={id}` (venues by user)

List endpoints tend to be used by admins for management purposes and are less latency-sensitive, so caching them adds complexity without much benefit.

---

## Redis connection

Redis is configured in `application.yml`:

```yaml
spring:
  data:
    redis:
      host: redis       # hostname (Docker service name)
      port: 6379
      password: redispass
```

In local development, Redis runs in Docker as part of the `docker-compose` stack.
