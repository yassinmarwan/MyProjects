# Authentication

User-service uses **JWT (JSON Web Token)** for authentication. This document explains what that means and how it works end to end.

---

## What is a JWT?

A JWT is a small, self-contained token — like a signed concert ticket. When you show it at the door, the bouncer can verify it is genuine without having to call the box office every single time.

When a user logs in, user-service creates a JWT and hands it back. The user includes this token in every subsequent request. The service verifies the token's signature to confirm it is authentic, then reads the user's information directly out of the token — no database lookup needed for every request.

A JWT has three parts separated by dots:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhaG1lZEBleGFtcGxlLmNvbSIsInVpZCI6MSwicm9sZSI6IkFUVEVOREVFIn0.signature
    [header]                              [payload]                                                    [signature]
```

The payload inside our tokens contains:
- `sub` — the user's email address
- `uid` — the user's database ID (a number)
- `role` — either `ATTENDEE` or `ADMIN`
- `iat` — when the token was issued
- `exp` — when the token expires

By default, tokens expire after **24 hours** (configurable via `JWT_EXPIRATION_MS`).

---

## Registration flow

What happens when someone calls `POST /api/auth/register`:

```
1. Controller receives the request body (name, email, password, phone)

2. AuthService validates:
   - All fields must be present and not blank → 400 if not
   - Email must not already exist in the database → 409 if it does
   - Phone must not already exist in the database → 409 if it does

3. Password is hashed with bcrypt
   (the real password is never stored — only an unrecoverable hash)

4. A new User row is saved to PostgreSQL with:
   - role = ATTENDEE
   - status = ACTIVE

5. A user.registered event is published to RabbitMQ
   (other services can react to this — e.g., booking-service)

6. A JWT token is created and returned to the caller
```

---

## Login flow

What happens when someone calls `POST /api/auth/login`:

```
1. Controller receives email and password

2. AuthService looks up the user by email
   → 401 "Invalid credentials" if not found

3. The provided password is compared against the stored bcrypt hash
   → 401 "Invalid credentials" if it doesn't match
   (same error message whether email or password is wrong — prevents guessing)

4. A LOGGED_IN event is written to MongoDB

5. A JWT token is created and returned to the caller
```

---

## Calling a protected endpoint

Every endpoint (except `/api/auth/**` and health checks) requires a valid JWT. Here is what happens for each request:

```
Client sends:
  GET /api/users/1/profile
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

The request passes through a chain of four security checks. If any step fails, the request stops and an error is returned immediately.

---

### Step 1 — TokenExtractionHandler

Looks for the `Authorization` header. Checks that it starts with `Bearer `. Pulls out the token string.

- Missing or malformed header → **401 Unauthorized**

---

### Step 2 — SignatureValidationHandler

Verifies that the token was signed with the correct secret key and has not expired.

- Invalid signature → **401 Unauthorized**
- Token expired → **401 Unauthorized**

If valid, the handler reads the email, user ID, and role out of the token and stores them in a shared context object (`AuthContext`) that the next steps can read.

---

### Step 3 — UserLoaderHandler

Looks up the user in the database by email to confirm the account still exists.

- User not found (e.g., account was deleted after the token was issued) → **401 Unauthorized**

---

### Step 4 — RoleAuthorizationHandler

Checks that the user has the required role for this endpoint.

- Only one endpoint requires the `ADMIN` role: `PUT /api/users/{id}/role`
- All other authenticated endpoints only require `ATTENDEE` (i.e., any logged-in user)

If the user does not have the required role → **403 Forbidden**

---

### After all checks pass

The user's identity (email, ID, role) is placed into Spring's security context. Controllers can then read this to enforce "owner or admin" rules (see below).

---

## Public vs protected endpoints

| Endpoint | Auth required? |
|----------|---------------|
| `POST /api/auth/register` | No |
| `POST /api/auth/login` | No |
| `GET /api/users/health` | No |
| All other endpoints | Yes — valid JWT required |

---

## The "owner or admin" rule

Some endpoints can only be called by the user themselves or by an admin. For example, you should not be able to view another user's profile or delete their account.

This check happens inside the controller:

```
Caller wants: GET /api/users/42/profile

Is the caller an admin?
  → Yes → allow

Is the caller's ID equal to 42?
  → Yes → allow

Otherwise → 403 Forbidden
```

Endpoints that enforce this rule:
- `GET /api/users/{id}`
- `PUT /api/users/{id}`
- `DELETE /api/users/{id}`
- `GET /api/users/{id}/activity`

---

## Token configuration

| Setting | Environment variable | Default |
|---------|---------------------|---------|
| Signing secret | `JWT_SECRET` | A default value (change in production!) |
| Expiry time | `JWT_EXPIRATION_MS` | `86400000` (24 hours in milliseconds) |

In production, always set `JWT_SECRET` to a long, randomly-generated string. Never use the default.
