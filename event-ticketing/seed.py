#!/usr/bin/env python3
"""
Seed script — no external dependencies required.
Uses docker exec to run psql and mongosh directly inside the containers.
Password for all users: password123

Test scenario:
  User ID=1: Ahmed Hassan
  5 bookings for userId=1:
    3 COMPLETED (300, 500, 700) → totalSpent=1500, avg=500
    1 CANCELLED
    1 PENDING
"""

import subprocess
import sys
from datetime import datetime, timezone

USER_PG_CONTAINER    = "eventticketing-user-db"
BOOKING_PG_CONTAINER = "eventticketing-booking-db"
MONGO_CONTAINER      = "eventticketing-mongo"

# BCrypt hash of "password123"
PW = r"$2b$12$3OFZ31Rh3v.8rbqhrQDnc.CPsj5PJjcXkLay3fVfuOgBa3YjkxqE6"

def run(cmd, input_text=None):
    result = subprocess.run(cmd, input=input_text, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  ERROR: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(1)
    return result.stdout.strip()

# ── User Postgres (etdb-users) ────────────────────────────────────────────────

user_sql = f"""
INSERT INTO users (id, name, email, password, phone, role, status, preferences, created_at)
VALUES
  (1, 'Ahmed Hassan', 'ahmed@example.com',   '{PW}', '0100000001', 'ATTENDEE', 'ACTIVE', '{{}}', NOW()),
  (2, 'Bob',          'bob@example.com',     '{PW}', '0100000002', 'ATTENDEE', 'ACTIVE', '{{}}', NOW()),
  (3, 'Charlie',      'charlie@example.com', '{PW}', '0100000003', 'ATTENDEE', 'ACTIVE', '{{}}', NOW()),
  (4, 'Diana',        'diana@example.com',   '{PW}', '0100000004', 'ATTENDEE', 'ACTIVE', '{{}}', NOW()),
  (5, 'Eve',          'eve@example.com',     '{PW}', '0100000005', 'ADMIN',    'ACTIVE', '{{}}', NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('users','id'), (SELECT MAX(id) FROM users));
"""

print("Seeding user-postgres (etdb-users)...")
out = run(
    ["docker", "exec", "-i", USER_PG_CONTAINER,
     "psql", "-U", "postgres", "-d", "etdb-users"],
    input_text=user_sql
)
print(f"  Done.\n{out}")

# ── Booking Postgres (etdb-bookings) ──────────────────────────────────────────

booking_sql = """
INSERT INTO bookings (id, user_id, event_id, contact_email, status, total_amount, booking_date, confirmed_at)
VALUES
  (1, 1, 101, 'ahmed@example.com', 'COMPLETED', 300.00, NOW(), NOW()),
  (2, 1, 102, 'ahmed@example.com', 'COMPLETED', 500.00, NOW(), NOW()),
  (3, 1, 103, 'ahmed@example.com', 'COMPLETED', 700.00, NOW(), NOW()),
  (4, 1, 104, 'ahmed@example.com', 'CANCELLED',  50.00, NOW(), NULL),
  (5, 1, 105, 'ahmed@example.com', 'PENDING',    99.00, NOW(), NULL)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('bookings','id'), (SELECT MAX(id) FROM bookings));
"""

print("Seeding booking-postgres (etdb-bookings)...")
out = run(
    ["docker", "exec", "-i", BOOKING_PG_CONTAINER,
     "psql", "-U", "postgres", "-d", "etdb-bookings"],
    input_text=booking_sql
)
print(f"  Done.\n{out}")

# ── MongoDB ───────────────────────────────────────────────────────────────────

ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
mongo_js = f"""
use eventticketingmongo;
db.authEvents.insertMany([
  {{userId: 1, email: "ahmed@example.com",   action: "LOGIN",    details: "initial login", timestamp: new Date("{ts}")}},
  {{userId: 2, email: "bob@example.com",     action: "LOGIN",    details: "initial login", timestamp: new Date("{ts}")}},
  {{userId: 3, email: "charlie@example.com", action: "LOGIN",    details: "initial login", timestamp: new Date("{ts}")}},
  {{userId: 4, email: "diana@example.com",   action: "REGISTER", details: "signed up",     timestamp: new Date("{ts}")}},
  {{userId: 5, email: "eve@example.com",     action: "REGISTER", details: "admin created", timestamp: new Date("{ts}")}}
]);
"""

print("Seeding MongoDB (authEvents)...")
out = run(
    ["docker", "exec", "-i", MONGO_CONTAINER,
     "mongosh", "--username", "root", "--password", "rootpass",
     "--authenticationDatabase", "admin", "--quiet"],
    input_text=mongo_js
)
print(f"  Done.\n{out}")

print("""
All done! Login with:
  POST http://localhost:8081/api/auth/login
  Body: {"email": "ahmed@example.com", "password": "password123"}

Then test booking summary:
  GET http://localhost:8081/api/users/1/booking-summary
  Header: Authorization: Bearer <token>

Expected response:
  userId=1, name="Ahmed Hassan"
  totalBookings=5, completedBookings=3, cancelledBookings=1
  totalSpent=1500.00, averageBookingAmount=500.00
""")
