# Data Model

User-service uses three different storage systems, each for a different purpose.

---

## PostgreSQL — the main database

This is where the permanent user data lives. It is a traditional relational database.

### `users` table

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` | Auto-generated primary key |
| `name` | `VARCHAR` | Full display name, required |
| `email` | `VARCHAR` | Unique — no two users can share an email |
| `password` | `VARCHAR` | Stored as a bcrypt hash, never in plain text |
| `phone` | `VARCHAR` | Unique — no two users can share a phone number |
| `role` | `VARCHAR` | Either `ATTENDEE` or `ADMIN` |
| `status` | `VARCHAR` | Either `ACTIVE` or `DEACTIVATED`. Defaults to `ACTIVE` |
| `preferences` | `JSONB` | A flexible key-value store (see below) |
| `created_at` | `TIMESTAMP` | Set automatically when the row is created |

**About the password:** Passwords are never stored as-is. When a user registers, the password is run through a one-way hashing algorithm (bcrypt) before it is saved. This means even if the database were leaked, the real passwords would not be exposed.

### The `preferences` column (JSONB)

The `preferences` column is special — instead of having a fixed set of columns for every possible user setting, we store a flexible dictionary in a single column. PostgreSQL supports this natively with the `JSONB` type.

Think of it like a drawer where you can put anything you want, labeled with a key.

**Common preference keys:**

| Key | Type | Example value | Meaning |
|-----|------|---------------|---------|
| `language` | string | `"en"`, `"ar"` | UI language preference |
| `notifyByEmail` | boolean | `true` | Whether to send email notifications |
| `notifyBySms` | boolean | `false` | Whether to send SMS notifications |
| `favoriteCategory` | string | `"CONCERT"`, `"SPORTS"` | Preferred event category |
| `preferredTicketType` | string | `"VIP"`, `"standard"` | Default ticket type preference |
| `timezone` | string | `"Africa/Cairo"` | User's local timezone |
| `totalBookings` | number | `5` | Running total of bookings (updated by RabbitMQ consumer) |
| `totalSpent` | number | `1500.0` | Running total amount spent (updated by RabbitMQ consumer) |

**Note on `totalBookings` and `totalSpent`:** These are not authoritative — they are a local cache of data maintained by listening to events from booking-service. The authoritative counts always come from booking-service directly (via Feign). These preference keys are used internally.

---

### `favorite_venues` table

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` | Auto-generated primary key |
| `user_id` | `BIGINT` | Foreign key → `users.id` |
| `label` | `VARCHAR` | User-defined name, e.g. "My Go-To Spot" |
| `venue_name` | `VARCHAR` | Official venue name, e.g. "Cairo Opera House" |
| `location` | `VARCHAR` | Human-readable address |
| `capacity` | `INTEGER` | Venue capacity (optional) |
| `is_default` | `BOOLEAN` | Only one venue per user can be `true` at a time |
| `metadata` | `JSONB` | Flexible extra details about the venue |
| `created_at` | `TIMESTAMP` | Set automatically when the row is created |

**About the relationship:** Each user can have many favorite venues. Each venue belongs to exactly one user. The `user_id` column in `favorite_venues` is the link.

**Common metadata keys:**

| Key | Type | Example |
|-----|------|---------|
| `parkingAvailable` | boolean | `true` |
| `wheelchairAccess` | boolean | `true` |
| `elevators` | boolean | `false` |
| `foodOptions` | array of strings | `["café", "restaurant"]` |
| `nearbyTransport` | array of strings | `["Metro Line 1", "Bus 72"]` |
| `websiteUrl` | string | `"https://cairoopera.org"` |
| `personalNotes` | string | Any free-text note the user wants to add |

---

## Redis — the cache

Redis is an in-memory store used for caching. When a request comes in for data that was recently fetched, we return the cached result instead of running the database query again. This makes responses much faster.

**Think of it like:** a notepad where you jot down answers you looked up recently so you don't have to look them up again.

Every cache entry has a **TTL (time to live)** — after that time, the cached result is automatically deleted and the next request will fetch fresh data from the database.

| Cache name | TTL | What it stores |
|------------|-----|----------------|
| `user-service::user` | 15 min | A single user by ID |
| `user-service::favorite-venue` | 15 min | A single favorite venue by ID |
| `user-service::S1-F1` | 5 min | Search results (by name/email/role) |
| `user-service::S1-F3` | 10 min | Booking summary for a user |
| `user-service::S1-F5` | 5 min | Users filtered by preference key-value |
| `user-service::S1-F6` | 10 min | Top attendees report |
| `user-service::S1-F8` | 15 min | User profile (includes favorite venues) |
| `user-service::S1-F9` | 10 min | Users filtered by favorite category |
| `user-service::S1-F12` | 5 min | User activity feed (from MongoDB) |

**Cache key format:** `user-service::{cacheId}::{parameters}`

For example, looking up user with ID 5 uses the key `user-service::user::5`. A top-attendees query for March 2026 with limit 10 uses `user-service::S1-F6::2026-03-01:2026-03-31:10`.

**When is the cache cleared?** Whenever data changes, the affected cache entries are deleted (this is called "eviction"). For example, when a user's profile is updated, the cache for that user's profile, search results, and booking summary are all cleared so the next request gets fresh data. See [caching.md](caching.md) for the full eviction table.

---

## MongoDB — the audit log

MongoDB is a document database used to store activity logs. Every time something important happens (user registers, logs in, updates their profile, deactivates their account, etc.), a document is written to MongoDB. This creates a permanent audit trail.

**Why a separate database for logs?** Audit logs are write-heavy and append-only — you never update or delete them. A document database like MongoDB handles this very well. Keeping logs separate also means a failure in audit logging never affects the main user operations.

### Collections

| Collection | What it stores |
|------------|---------------|
| `auth_events` | User registrations, logins, profile updates, role changes, deactivations |
| `booking_events` | Booking-related events (written when user-service processes booking notifications) |
| `event_events` | Event-related activity logs |
| `ticket_events` | Ticket-related activity logs |
| `payment_audit_trail` | Payment and sales activity logs |

### `auth_events` document structure

```json
{
  "_id": "507f1f77bcf86cd799439011",
  "timestamp": "2026-03-15T14:30:00",
  "action": "LOGGED_IN",
  "details": { "userId": 1 },
  "userId": 1,
  "email": "ahmed@example.com"
}
```

**Actions written to `auth_events`:**
- `REGISTERED` — new user account created
- `LOGGED_IN` — user successfully logged in
- `USER_UPDATED` — profile fields changed
- `USER_DELETED` — account deleted
- `PREFERENCES_UPDATED` — preferences changed
- `ROLE_CHANGED` — role updated by admin
- `USER_DEACTIVATED` — account deactivated
- `VENUE_ADDED` — favorite venue added
- `VENUE_UPDATED` — favorite venue updated
- `VENUE_DELETED` — favorite venue removed
- `DEFAULT_VENUE_SET` — a venue marked as default
