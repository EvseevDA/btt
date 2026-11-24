CREATE TABLE trip (
    id uuid PRIMARY KEY,
    route_id uuid NOT NULL REFERENCES route (id),
    transporter_id uuid NOT NULL REFERENCES transporter (id),
    departure_at timestamp without time zone NOT NULL,
    arrival_at timestamp without time zone NOT NULL,
    status integer NOT NULL,
    CONSTRAINT ck_trip_times CHECK (arrival_at > departure_at)
);

CREATE INDEX ix_trip_route_id ON trip (route_id);
CREATE INDEX ix_trip_transporter_id ON trip (transporter_id);

CREATE TABLE ticket (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip (id),
    user_id uuid NOT NULL REFERENCES "user" (id),
    ticket_number varchar(100) NOT NULL UNIQUE,
    seat_code varchar(20),
    price decimal(12,2) NOT NULL,
    status integer NOT NULL,
    booked_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_nonnegative_price CHECK (price >= 0)
);

CREATE INDEX ix_ticket_trip_id ON ticket (trip_id);
CREATE INDEX ix_ticket_user_id ON ticket (user_id);
