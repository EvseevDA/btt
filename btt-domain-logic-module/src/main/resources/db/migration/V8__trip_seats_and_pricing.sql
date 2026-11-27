ALTER TABLE trip ADD COLUMN base_price numeric(12,2) CHECK (base_price >= 0);

CREATE TABLE trip_seat (
    trip_id uuid NOT NULL REFERENCES trip (id),
    seat_code varchar(20) NOT NULL CHECK (length(btrim(seat_code)) > 0),
    PRIMARY KEY (trip_id, seat_code)
);

INSERT INTO trip_seat (trip_id, seat_code)
SELECT DISTINCT trip_id, seat_code FROM ticket WHERE seat_code IS NOT NULL;

ALTER TABLE ticket ADD CONSTRAINT fk_ticket_trip_seat
    FOREIGN KEY (trip_id, seat_code) REFERENCES trip_seat (trip_id, seat_code);

CREATE UNIQUE INDEX uq_ticket_booked_seat
    ON ticket (trip_id, seat_code) WHERE status = 1 AND seat_code IS NOT NULL;

CREATE INDEX ix_trip_scheduled_departure ON trip (departure_at, id) WHERE status = 1;
CREATE INDEX ix_ticket_user_booked_at ON ticket (user_id, booked_at DESC, id);
