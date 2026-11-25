ALTER TABLE "user" ADD COLUMN password_hash varchar(255);

CREATE TABLE role_permission (
    role_id uuid NOT NULL REFERENCES role (id),
    permission varchar(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

ALTER TABLE transporter_organization_users
    ADD COLUMN can_represent boolean NOT NULL DEFAULT false,
    ADD COLUMN can_confirm_members boolean NOT NULL DEFAULT false;

CREATE TABLE organization_request (
    id uuid PRIMARY KEY,
    applicant_id uuid NOT NULL REFERENCES "user" (id),
    organization_id uuid REFERENCES transporter_organization (id),
    type integer NOT NULL CHECK (type IN (1, 2)),
    status integer NOT NULL CHECK (status IN (1, 2, 3)),
    organization_name varchar(255),
    inn varchar(12),
    ogrn varchar(15),
    legal_address text,
    authority_evidence text,
    decided_by_id uuid REFERENCES "user" (id),
    decision_comment text,
    created_at timestamp NOT NULL,
    decided_at timestamp,
    CHECK (type <> 2 OR organization_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_pending_organization_membership
    ON organization_request (organization_id, applicant_id) WHERE type = 2 AND status = 1;
CREATE UNIQUE INDEX uq_pending_organization_registration_inn
    ON organization_request (inn) WHERE type = 1 AND status = 1;
CREATE UNIQUE INDEX uq_pending_organization_registration_ogrn
    ON organization_request (ogrn) WHERE type = 1 AND status = 1;

CREATE TABLE transporter_version (
    id uuid PRIMARY KEY,
    transporter_id uuid NOT NULL REFERENCES transporter (id),
    version_number integer NOT NULL CHECK (version_number > 0),
    transport_kind_id uuid NOT NULL REFERENCES transport_kind (id),
    status integer NOT NULL,
    created_at timestamp NOT NULL,
    UNIQUE (transporter_id, version_number),
    UNIQUE (transporter_id, id)
);

INSERT INTO transporter_version (id, transporter_id, version_number, transport_kind_id, status, created_at)
SELECT id, id, 1, transport_kind_id, status, created_at FROM transporter;

ALTER TABLE transporter_license ADD COLUMN transporter_version_id uuid;
UPDATE transporter_license SET transporter_version_id = transporter_id;
ALTER TABLE transporter_license ALTER COLUMN transporter_version_id SET NOT NULL;
ALTER TABLE transporter_license ADD CONSTRAINT fk_license_transporter_version
    FOREIGN KEY (transporter_id, transporter_version_id) REFERENCES transporter_version (transporter_id, id);
CREATE INDEX ix_license_transporter_version ON transporter_license (transporter_version_id);

ALTER TABLE transporter_registration_certificate_version
    ADD COLUMN transporter_version_id uuid REFERENCES transporter_version (id);
UPDATE transporter_registration_certificate_version v
SET transporter_version_id = c.transporter_id
FROM transporter_registration_certificate c WHERE c.id = v.certificate_id;
ALTER TABLE transporter_registration_certificate_version ALTER COLUMN transporter_version_id SET NOT NULL;

ALTER TABLE request ALTER COLUMN transporter_id DROP NOT NULL;
ALTER TABLE request
    ADD COLUMN organization_id uuid REFERENCES transporter_organization (id),
    ADD COLUMN source_transporter_version_id uuid REFERENCES transporter_version (id),
    ADD COLUMN proposed_data text;
UPDATE request r SET organization_id = t.organization_id, source_transporter_version_id = t.id
FROM transporter t WHERE t.id = r.transporter_id;
ALTER TABLE request ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX ix_request_organization ON request (organization_id);

ALTER TABLE decision
    ADD COLUMN approved_by_id uuid REFERENCES "user" (id),
    ADD COLUMN approved_at timestamp,
    ADD COLUMN return_reason text,
    ADD COLUMN change_description text,
    ADD COLUMN revision integer NOT NULL DEFAULT 1;

CREATE TABLE workflow_command (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL,
    request_kind varchar(40) NOT NULL,
    process_id varchar(100),
    message_name varchar(100),
    variables text NOT NULL,
    created_at timestamp NOT NULL,
    delivered_at timestamp,
    CHECK ((process_id IS NULL) <> (message_name IS NULL))
);
CREATE INDEX ix_workflow_command_pending ON workflow_command (created_at) WHERE delivered_at IS NULL;
