-- Supporting tables required by booking-service business logic but not JPA-mapped here.
-- ticket_sales: populated by S3-F4 completeBooking via native INSERT
CREATE TABLE IF NOT EXISTS ticket_sales (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DOUBLE PRECISION,
    method VARCHAR(50),
    status VARCHAR(50),
    created_at TIMESTAMP,
    transaction_details JSONB DEFAULT '{}'
);

-- tickets: updated by S3-F2 cancelBooking via native UPDATE
CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT,
    status VARCHAR(50)
);
