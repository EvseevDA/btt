ALTER TABLE request ADD COLUMN pending_action varchar(100);
ALTER TABLE request ADD COLUMN pending_command_id uuid;
ALTER TABLE request ADD CONSTRAINT ck_request_pending_command
    CHECK ((pending_action IS NULL) = (pending_command_id IS NULL));

CREATE TABLE registration_processed_command (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES request (id),
    decision_type integer
);
