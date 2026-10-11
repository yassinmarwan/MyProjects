# Configuration

This document covers all the settings that control how user-service behaves. Settings live in `src/main/resources/application.yml` and can be overridden with environment variables.

---

## Environment variables

These are the values you should set in production. In local development, the defaults in `application.yml` work out of the box (assuming Docker Compose is running).

| Variable | Default (dev) | Description |
|----------|--------------|-------------|
| `JWT_SECRET` | A built-in default | The secret key used to sign JWT tokens. **Must be changed in production** — use a long random string (64+ characters). |
| `JWT_EXPIRATION_MS` | `86400000` | How long a JWT token is valid, in milliseconds. Default is 24 hours (86,400,000 ms). |
| `BOOKING_SERVICE_URL` | `http://booking-service:8080` | The base URL of booking-service. In Docker Compose this is the service name. In Kubernetes it would be the cluster-internal DNS name. |
| `RABBITMQ_HOST` | `rabbitmq` | Hostname of the RabbitMQ server |
| `RABBITMQ_PORT` | `5672` | Port of the RabbitMQ server |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password. Change in production. |

---

## application.yml walkthrough

Here is the full config file with explanations for each section:

```yaml
# ─── PostgreSQL (main database) ───────────────────────────────────────────────
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/eventticketingdb
    # ^ "postgres" is the Docker Compose service name for the database container
    # "5432" is PostgreSQL's default port
    # "eventticketingdb" is the database name
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: update
      # ^ "update" means Hibernate will automatically create/alter tables to match
      #   the Java entity classes. Fine for development. In production, use
      #   "validate" (check the schema matches) or manage migrations manually.
    show-sql: true
    # ^ Prints every SQL query to the logs. Useful for debugging; turn off in
    #   production to reduce log noise.

# ─── Redis (cache) ────────────────────────────────────────────────────────────
  data:
    redis:
      host: redis       # Docker Compose service name for Redis
      port: 6379
      password: redispass

# ─── MongoDB (audit logs) ─────────────────────────────────────────────────────
  mongodb:
    uri: mongodb://root:rootpass@mongo:27017/eventticketingmongo?authSource=admin
    # ^ "mongo" is the Docker Compose service name
    # "27017" is MongoDB's default port
    # "eventticketingmongo" is the database name
    # "authSource=admin" means credentials are stored in the "admin" database

# ─── RabbitMQ (messaging) ─────────────────────────────────────────────────────
  rabbitmq:
    host: rabbitmq      # Docker Compose service name for RabbitMQ
    port: 5672          # RabbitMQ's default AMQP port (15672 is the management UI)
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: auto
        # ^ Messages are automatically acknowledged (confirmed received) as soon
        #   as the listener method returns without throwing an exception.
        default-requeue-rejected: false
        # ^ If a message fails and retries are exhausted, do NOT put it back in
        #   the queue — instead, route it to the Dead Letter Queue (DLQ).
        retry:
          enabled: true
          initial-interval: 1000   # Wait 1 second before first retry
          max-attempts: 3          # Try up to 3 times total before giving up

# ─── JWT ──────────────────────────────────────────────────────────────────────
jwt:
  secret: ${JWT_SECRET:24966c8a763b3a3f7f264cefb93d95159184e8c5e2af5c94e05387371d47a3fd=}
  # ^ Reads from the JWT_SECRET environment variable. If not set, uses the
  #   value after the colon as a fallback. NEVER use the fallback in production.
  expiration: ${JWT_EXPIRATION_MS:86400000}
  # ^ Token lifetime in milliseconds. 86400000 = 24 hours.

# ─── Feign (HTTP calls to other services) ─────────────────────────────────────
feign:
  booking-service:
    url: ${BOOKING_SERVICE_URL:http://booking-service:8080}
    # ^ The base URL that all BookingServiceClient calls are sent to.
    #   In Docker Compose, "booking-service" resolves to the booking-service container.
```

---

## How environment variable fallbacks work

The `${VARIABLE_NAME:default_value}` syntax means:
1. First, check if the environment variable `VARIABLE_NAME` is set
2. If yes, use that value
3. If no, use `default_value`

For example:
```yaml
url: ${BOOKING_SERVICE_URL:http://booking-service:8080}
```
- In Docker Compose (where `BOOKING_SERVICE_URL` is not set): uses `http://booking-service:8080`
- In Kubernetes (where you set `BOOKING_SERVICE_URL=http://booking-service.default.svc.cluster.local`): uses that

---

## Production checklist

Before deploying to production, make sure you have:

- [ ] Set `JWT_SECRET` to a long random string — never use the default
- [ ] Set database credentials to something other than `postgres/postgres`
- [ ] Set RabbitMQ credentials to something other than `guest/guest`
- [ ] Set `BOOKING_SERVICE_URL` to the correct internal URL for your environment
- [ ] Consider setting `spring.jpa.hibernate.ddl-auto: validate` instead of `update`
- [ ] Consider setting `spring.jpa.show-sql: false` to reduce log volume
