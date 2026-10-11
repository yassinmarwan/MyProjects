# API Reference

All endpoints are relative to the service base URL. In production, requests go through the API gateway. In local development, call the service directly on its port.

**Authentication:** All endpoints except `/api/auth/**` and health checks require a Bearer token in the `Authorization` header:
```
Authorization: Bearer <your-jwt-token>
```

---

## Auth endpoints

### Register a new user

```
POST /api/auth/register
```

No token required. Creates a new user account with the `ATTENDEE` role.

**Request body:**
```json
{
  "name": "Ahmed Hassan",
  "email": "ahmed@example.com",
  "password": "securepassword123",
  "phone": "+201012345678"
}
```

**Success response — 201 Created:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000
}
```

**Error cases:**
- `400` — any field is blank
- `409` — email or phone already registered

---

### Login

```
POST /api/auth/login
```

No token required. Returns a JWT token if credentials are correct.

**Request body:**
```json
{
  "email": "ahmed@example.com",
  "password": "securepassword123"
}
```

**Success response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000
}
```

**Error cases:**
- `401` — wrong email or wrong password (same message for both — we don't reveal which)

---

## User endpoints

### Create a user (direct, admin use)

```
POST /api/users
```

Creates a user directly without going through the registration flow. Requires authentication.

**Request body:** A `User` JSON object with name, email, password, phone, role.

**Success response — 201 Created:** The created user object.

---

### List all users

```
GET /api/users
```

Returns every user in the system.

**Success response — 200 OK:** Array of user objects.

---

### Get a user by ID

```
GET /api/users/{id}
```

**Who can call this:** The user themselves, or an admin. Anyone else gets a 403.

**Success response — 200 OK:**
```json
{
  "id": 1,
  "name": "Ahmed Hassan",
  "email": "ahmed@example.com",
  "phone": "+201012345678",
  "role": "ATTENDEE",
  "status": "ACTIVE",
  "preferences": {
    "language": "en",
    "notifyByEmail": true,
    "favoriteCategory": "CONCERT"
  },
  "createdAt": "2026-03-01T10:00:00"
}
```

**Error cases:**
- `403` — caller is not the owner and not an admin
- `404` — user not found

---

### Update a user

```
PUT /api/users/{id}
```

**Who can call this:** The user themselves, or an admin.

Partial update — only include the fields you want to change. Fields you leave out stay the same.

**Request body example (change name only):**
```json
{
  "name": "Ahmed M. Hassan"
}
```

**Success response — 200 OK:** The updated user object.

---

### Delete a user

```
DELETE /api/users/{id}
```

**Who can call this:** The user themselves, or an admin.

**Success response — 204 No Content**

---

### Update preferences

```
PUT /api/users/{id}/preferences
```

Merges new preferences into the existing ones. Existing keys you don't include are kept as-is.

**Request body:**
```json
{
  "language": "ar",
  "notifyByEmail": false,
  "favoriteCategory": "SPORTS"
}
```

**Success response — 200 OK:** The updated user object.

---

### Deactivate a user account

```
PUT /api/users/{id}/deactivate
```

Sets the user's status to `DEACTIVATED`. This will fail if the user has any active (pending, confirmed, or checked-in) bookings.

**Success response — 200 OK:** The updated user object with `status: "DEACTIVATED"`.

**Error cases:**
- `400` — user has active bookings
- `404` — user not found

After a successful deactivation, a `user.deactivated` event is published to RabbitMQ.

---

### Get booking summary

```
GET /api/users/{id}/booking-summary
```

Returns aggregated booking statistics for this user. The numbers come from booking-service via an HTTP call — they are not stored in user-service's database.

**Success response — 200 OK:**
```json
{
  "userId": 1,
  "name": "Ahmed Hassan",
  "totalBookings": 5,
  "completedBookings": 3,
  "cancelledBookings": 1,
  "totalSpent": 1500.00,
  "averageBookingAmount": 500.00
}
```

---

### Get user profile

```
GET /api/users/{id}/profile
```

Returns a richer view of the user, including their list of favorite venues.

**Success response — 200 OK:**
```json
{
  "userId": 1,
  "name": "Ahmed Hassan",
  "email": "ahmed@example.com",
  "phone": "+201012345678",
  "preferences": { "language": "en" },
  "favoriteVenues": [
    {
      "id": 10,
      "label": "My favourite",
      "venueName": "Cairo Opera House",
      "location": "Zamalek, Cairo",
      "isDefault": true
    }
  ],
  "totalFavoriteVenues": 1
}
```

---

### Get activity feed

```
GET /api/users/{id}/activity?page=0&size=10
```

Returns a paginated list of events from this user's activity log (stored in MongoDB). Includes registrations, logins, profile updates, etc.

**Who can call this:** The user themselves, or an admin.

**Query parameters:**
- `page` — page number, starting from 0 (default: 0)
- `size` — number of results per page, max 100 (default: 10)

**Success response — 200 OK:**
```json
{
  "content": [
    {
      "id": "abc123",
      "timestamp": "2026-03-15T14:30:00",
      "action": "LOGGED_IN",
      "details": {},
      "userId": 1,
      "email": "ahmed@example.com"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42
}
```

---

### Update user role (admin only)

```
PUT /api/users/{id}/role
```

**Who can call this:** Admins only. Any other caller gets a 403.

**Request body:**
```json
{
  "role": "ADMIN"
}
```

Valid values: `ATTENDEE`, `ADMIN`

**Success response — 200 OK:** The updated user object.

---

### Search users

```
GET /api/users/search?name=ahmed&email=example&role=ATTENDEE
```

All parameters are optional. Searches are case-insensitive and use partial matching (so `name=ahmed` matches "Ahmed Hassan").

**Query parameters:**
- `name` — partial name match
- `email` — partial email match
- `role` — exact role match (`ATTENDEE` or `ADMIN`)

**Success response — 200 OK:** Array of matching user objects.

---

### Filter users by preference key-value

```
GET /api/users/preferences/search?key=language&value=en
```

Returns all users whose preferences contain the given key with the given value. Both parameters are required.

**Success response — 200 OK:** Array of matching user objects.

**Error cases:**
- `400` — key or value is blank

---

### Filter users by favorite category

```
GET /api/users/preferences/category?category=CONCERT&minBookings=3
```

Returns users whose `favoriteCategory` preference matches the given value AND who have at least `minBookings` completed bookings. The booking count is checked live against booking-service.

**Query parameters:**
- `category` — required, e.g. `CONCERT`, `SPORTS`, `THEATRE`
- `minBookings` — minimum number of completed bookings (default: 0)

**Success response — 200 OK:** Array of matching user objects.

**Error cases:**
- `400` — category is blank

---

## Reports endpoints

### Top attendees by spending

```
GET /api/users/reports/top-attendees?startDate=2026-03-01&endDate=2026-03-31&limit=10
```

Returns the users who have spent the most on completed bookings within the given date range. For each user, user-service calls booking-service to get their total spending in that period. The results are sorted by total spending, highest first.

**Query parameters:**
- `startDate` — start of the date range (format: `YYYY-MM-DD`), required
- `endDate` — end of the date range (format: `YYYY-MM-DD`), required
- `limit` — maximum number of results to return, required

**Success response — 200 OK:**
```json
[
  {
    "userId": 2,
    "name": "Sara Ali",
    "totalSpent": 5000.00,
    "bookingCount": 8
  },
  {
    "userId": 1,
    "name": "Ahmed Hassan",
    "totalSpent": 2000.00,
    "bookingCount": 3
  }
]
```

**Error cases:**
- `400` — startDate is after endDate

---

## Favorite Venue endpoints

Favorite venues are venues a user has saved to their profile.

### Create a favorite venue

```
POST /api/favorite-venues?userId={userId}
```

**Request body:**
```json
{
  "label": "My Go-To Spot",
  "venueName": "Cairo Opera House",
  "location": "Zamalek, Cairo",
  "capacity": 1200,
  "metadata": {
    "parkingAvailable": true,
    "wheelchairAccess": true,
    "foodOptions": ["café", "restaurant"]
  }
}
```

**Success response — 201 Created:** The created venue object.

---

### List all favorite venues

```
GET /api/favorite-venues
```

Returns all favorite venues across all users.

---

### List favorite venues for a user

```
GET /api/favorite-venues?userId={userId}
```

Returns all favorite venues belonging to a specific user.

---

### Get a favorite venue by ID

```
GET /api/favorite-venues/{id}
```

---

### Update a favorite venue

```
PUT /api/favorite-venues/{id}
```

Partial update — only include fields you want to change.

---

### Delete a favorite venue

```
DELETE /api/favorite-venues/{id}
```

**Success response — 204 No Content**

---

### Set a venue as default

```
PUT /api/users/{userId}/venues/{venueId}/default
```

Marks a specific venue as this user's default venue. Clears the default flag from all other venues this user has first.

**Error cases:**
- `400` — the venue does not belong to this user
- `404` — user or venue not found

---

## Health check

```
GET /api/users/health
```

No authentication required. Returns `OK` if the service is running.
