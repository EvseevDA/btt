CREATE TABLE request (
    id uuid PRIMARY KEY,
    transporter_id uuid NOT NULL REFERENCES transporter (id),
    applicant_contact_id uuid NOT NULL REFERENCES "user" (id),
    executor_id uuid REFERENCES "user" (id),
    type integer NOT NULL,
    status integer NOT NULL,
    created_at timestamp without time zone NOT NULL,
    submitted_at timestamp without time zone
);

CREATE INDEX ix_request_transporter_id ON request (transporter_id);
CREATE INDEX ix_request_applicant_contact_id ON request (applicant_contact_id);
CREATE INDEX ix_request_executor_id ON request (executor_id);

CREATE TABLE decision (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE REFERENCES request (id),
    decided_by_id uuid NOT NULL REFERENCES "user" (id),
    type integer NOT NULL,
    comment text,
    status integer NOT NULL,
    decided_at timestamp without time zone
);

CREATE INDEX ix_decision_decided_by_id ON decision (decided_by_id);

CREATE TABLE transporter_registration_certificate (
    id uuid PRIMARY KEY,
    transporter_id uuid NOT NULL UNIQUE REFERENCES transporter (id),
    certificate_number varchar(100) NOT NULL UNIQUE,
    status integer NOT NULL,
    created_at timestamp without time zone NOT NULL
);

CREATE TABLE transporter_registration_certificate_version (
    id uuid PRIMARY KEY,
    certificate_id uuid NOT NULL REFERENCES transporter_registration_certificate (id),
    version_number integer NOT NULL,
    status integer NOT NULL,
    source_version_id uuid REFERENCES transporter_registration_certificate_version (id),
    previous_version_id uuid REFERENCES transporter_registration_certificate_version (id),
    change_description text,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT uq_certificate_version UNIQUE (certificate_id, version_number),
    CONSTRAINT ck_positive_version CHECK (version_number > 0),
    CONSTRAINT ck_different_source CHECK (source_version_id <> id),
    CONSTRAINT ck_different_previous CHECK (previous_version_id <> id)
);

CREATE INDEX ix_certificate_version_source ON transporter_registration_certificate_version (source_version_id);
CREATE INDEX ix_certificate_version_previous ON transporter_registration_certificate_version (previous_version_id);
