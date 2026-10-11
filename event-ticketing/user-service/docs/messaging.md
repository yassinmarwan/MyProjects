# Messaging (RabbitMQ)

User-service communicates with other services asynchronously using **RabbitMQ** — a message broker.

---

## What is a message broker?

Think of RabbitMQ like a post office. Instead of calling someone directly, you drop a letter in the post box. The post office delivers it to whoever it's addressed to — even if they're not home right now, the letter waits for them.

This is useful because:
- The sender doesn't have to wait for the receiver to respond
- If the receiver is temporarily down, messages queue up and are delivered when it comes back
- Services are loosely connected — user-service doesn't need to know anything about booking-service's internals, it just knows a routing key to use

---

## Key concepts

**Exchange** — the entry point for messages. You send a message to an exchange with a routing key (like an address). The exchange decides which queue(s) to deliver it to.

**Routing key** — a dot-separated string like `user.registered` or `booking.completed`. Think of it as the subject line of the letter.

**Queue** — a mailbox. Consumers listen on a queue and process messages one by one.

**Topic exchange** — a type of exchange that routes messages based on wildcard patterns. For example, a queue can subscribe to `booking.#` to receive all messages starting with `booking.`.

**DLQ (Dead Letter Queue)** — if a message fails to be processed 3 times in a row, it gets moved here automatically. This prevents a bad message from blocking the queue forever, and lets you inspect failed messages later.

---

## What user-service publishes

User-service publishes to the **`user.events`** exchange (type: TopicExchange).

### `user.registered`

**When:** After a successful call to `POST /api/auth/register`

**Payload:**
```json
{
  "userId": 1,
  "email": "ahmed@example.com",
  "role": "ATTENDEE"
}
```

**Who consumes this:** booking-service (and potentially others in the future)

---

### `user.deactivated`

**When:** After a successful call to `PUT /api/users/{id}/deactivate`

**Payload:**
```json
{
  "userId": 1
}
```

**Who consumes this:** booking-service (to cancel any pending bookings, etc.)

---

## What user-service consumes

User-service listens on the **`user.booking.saga-listener`** queue. This queue is bound to the **`booking.events`** exchange with the routing pattern `booking.#`, which means it receives all messages whose routing key starts with `booking.`.

### `booking.completed`

**When sent by:** booking-service, when a booking reaches the COMPLETED status

**Payload:**
```json
{
  "bookingId": 42,
  "userId": 1,
  "eventId": 7,
  "totalAmount": 500.00
}
```

**What user-service does with it:**
- Increments `totalBookings` by 1 in the user's preferences
- Adds `totalAmount` to `totalSpent` in the user's preferences

---

### `booking.cancelled`

**When sent by:** booking-service, when a booking is cancelled

**Payload:**
```json
{
  "bookingId": 42,
  "userId": 1,
  "eventId": 7,
  "reason": "Customer requested cancellation"
}
```

**What user-service does with it:**
- Decrements `totalBookings` by 1 (minimum 0)
- Does **not** change `totalSpent` — because the booking was cancelled before it was ever completed, so it was never added to `totalSpent` in the first place

---

## Retry and Dead Letter Queue (DLQ)

If processing a message fails (e.g., a database error), RabbitMQ automatically retries it. The retry configuration is:

- **Max attempts:** 3
- **Wait between attempts:** 1 second
- **After all retries fail:** the message is moved to the DLQ (`user.booking.saga-listener.dlq`)

Messages in the DLQ are not lost — they can be inspected and requeued manually if needed. Check the RabbitMQ management UI (port 15672) to see failed messages.

---

## Idempotency

Because RabbitMQ guarantees "at-least-once" delivery, the same message might be delivered more than once (for example, if the service crashes mid-processing and restarts). Processing the same `booking.completed` event twice would double-count the booking.

To prevent this, the consumer checks whether it has already processed each message. It stores a marker in the user's preferences:

```
preferences["processed:booking.completed:42"] = true
```

If this key already exists when the message arrives, the consumer skips processing and does nothing.

---

## RabbitMQ topology

```
user-service (producer)
  └── publishes to:  user.events  (TopicExchange)
                         ├── user.registered
                         └── user.deactivated

booking-service (producer)
  └── publishes to:  booking.events  (TopicExchange)
                         ├── booking.completed  ──┐
                         └── booking.cancelled  ──┤
                                                   ▼
                                   user.booking.saga-listener  (Queue)
                                   [binding pattern: booking.#]
                                           │
                                           ▼
                                   BookingEventConsumer
                                           │
                                   on failure (3 attempts):
                                           ▼
                                   user.booking.saga-listener.dlq
```

---

## RabbitMQ management UI

When running locally with Docker, you can view all exchanges, queues, and messages at:

```
http://localhost:15672
Username: guest
Password: guest
```
