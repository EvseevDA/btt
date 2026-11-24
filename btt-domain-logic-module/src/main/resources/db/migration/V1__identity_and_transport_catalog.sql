CREATE TABLE "user" (
    id uuid PRIMARY KEY,
    username varchar(100) NOT NULL UNIQUE,
    email varchar(255) NOT NULL UNIQUE,
    full_name varchar(255) NOT NULL,
    created_at timestamp without time zone NOT NULL
);

CREATE TABLE role (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE,
    description varchar(255)
);

CREATE TABLE user_roles (
    user_id uuid REFERENCES "user" (id),
    role_id uuid REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE transporter_organization (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    inn varchar(12) NOT NULL UNIQUE,
    ogrn varchar(15) NOT NULL UNIQUE,
    legal_address text NOT NULL,
    created_at timestamp without time zone NOT NULL
);

CREATE TABLE transporter_organization_users (
    organization_id uuid REFERENCES transporter_organization (id),
    user_id uuid REFERENCES "user" (id),
    PRIMARY KEY (organization_id, user_id)
);

CREATE INDEX ix_transporter_organization_users_user_id ON transporter_organization_users (user_id);

CREATE TABLE transport_kind (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL UNIQUE,
    description text
);

CREATE TABLE transporter (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES transporter_organization (id),
    transport_kind_id uuid NOT NULL REFERENCES transport_kind (id),
    status integer NOT NULL,
    created_at timestamp without time zone NOT NULL
);

CREATE INDEX ix_transporter_organization_id ON transporter (organization_id);
CREATE INDEX ix_transporter_transport_kind_id ON transporter (transport_kind_id);

CREATE TABLE transporter_license (
    id uuid PRIMARY KEY,
    transporter_id uuid NOT NULL REFERENCES transporter (id),
    license_number varchar(100) NOT NULL,
    issued_at date NOT NULL,
    expires_at date NOT NULL,
    issuing_authority varchar(255) NOT NULL,
    status integer NOT NULL,
    CONSTRAINT ck_license_dates CHECK (expires_at >= issued_at)
);

CREATE INDEX ix_transporter_license_transporter_id ON transporter_license (transporter_id);

CREATE TABLE route (
    id uuid PRIMARY KEY,
    source varchar(255) NOT NULL,
    dest varchar(255) NOT NULL,
    duration integer NOT NULL,
    transport_kind_id uuid NOT NULL REFERENCES transport_kind (id),
    CONSTRAINT ck_positive_duration CHECK (duration > 0)
);

CREATE INDEX ix_route_transport_kind_id ON route (transport_kind_id);
