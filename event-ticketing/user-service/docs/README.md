# User Service

The user service is responsible for everything related to user accounts in the Event Ticketing platform. It handles registration, login, user profiles, preferences, favorite venues, and booking statistics.

## What this service owns

- **User accounts** — names, emails, passwords, phone numbers, roles
- **User preferences** — flexible settings stored per user (language, notification preferences, favorite categories, etc.)
- **Favorite venues** — a list of venues each user has saved
- **Booking statistics** — summaries of how much a user has spent and how many bookings they have made (fetched live from booking-service)

## What this service does NOT own

This service does not store or manage bookings, tickets, events, or payments. Those live in their own dedicated services. When user-service needs booking data (for example, to show a user's total spending), it asks booking-service over HTTP — it does not touch the bookings database directly.

## Quick-start (running locally)

**Prerequisites:** Java 25, Maven, Docker (for PostgreSQL, Redis, MongoDB, RabbitMQ)

```bash
# From the repo root, start all infrastructure
docker-compose up -d postgres redis mongo rabbitmq

# Build and run the user-service
cd user-service
mvn spring-boot:run
```

The service starts on port **8080** by default (or whatever the gateway routes to).

Health check: `GET http://localhost:8080/api/users/health` → should return `OK`

## Documentation index

| File | What it covers |
|------|---------------|
| [architecture.md](architecture.md) | How the layers connect, design patterns used |
| [api-reference.md](api-reference.md) | Every endpoint with examples |
| [data-model.md](data-model.md) | Database tables, columns, and storage choices |
| [authentication.md](authentication.md) | JWT login flow and how requests are secured |
| [messaging.md](messaging.md) | RabbitMQ events published and consumed |
| [caching.md](caching.md) | Redis caching strategy and cache lifetimes |
| [circuit-breaker.md](circuit-breaker.md) | How the service stays up when booking-service goes down |
| [configuration.md](configuration.md) | Environment variables and application settings |
| [kubernetes.md](kubernetes.md) | Kubernetes manifests — secrets, configmap, deployment, services |
