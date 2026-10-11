CREATE TABLE IF NOT EXISTS bookings (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    status       VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19,2),
    booking_date TIMESTAMP
);
