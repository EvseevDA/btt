ALTER TABLE workflow_command
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN next_attempt_at timestamp NOT NULL DEFAULT timezone('UTC', now());

CREATE INDEX ix_workflow_pending_request
    ON workflow_command (request_id, created_at, id) WHERE delivered_at IS NULL;
