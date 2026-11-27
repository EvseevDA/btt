CREATE TABLE account_token (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES "user" (id),
    expires_at timestamp NOT NULL
);
CREATE INDEX ix_account_token_expiry ON account_token (expires_at);

INSERT INTO role (id, name, description) VALUES
    (md5('btt:PORTAL_ADMIN')::uuid, 'PORTAL_ADMIN', 'Администратор портала'),
    (md5('btt:REGISTRATION_MANAGER')::uuid, 'REGISTRATION_MANAGER', 'Начальник отдела по рассмотрению заявлений'),
    (md5('btt:REGISTRATION_EXECUTOR')::uuid, 'REGISTRATION_EXECUTOR', 'Исполнитель по заявлениям'),
    (md5('btt:REGISTRATION_APPROVER')::uuid, 'REGISTRATION_APPROVER', 'Согласующий решения')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, a.permission
FROM role r CROSS JOIN (VALUES ('account.staff.create'), ('organization.registration.decide'),
    ('transporter.admin-cancellation.submit')) a(permission)
WHERE r.name = 'PORTAL_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.prefix || '.' || action.name
FROM role r
CROSS JOIN (VALUES ('transporter.registration'), ('transporter.amendment'),
    ('transporter.carrier-cancellation'), ('transporter.admin-cancellation')) p(prefix)
CROSS JOIN (VALUES ('assign'), ('decide'), ('approve')) action(name)
WHERE r.name = 'PORTAL_ADMIN'
   OR (r.name = 'REGISTRATION_MANAGER' AND action.name IN ('assign', 'decide'))
   OR (r.name = 'REGISTRATION_EXECUTOR' AND action.name = 'decide')
   OR (r.name = 'REGISTRATION_APPROVER' AND action.name = 'approve')
ON CONFLICT DO NOTHING;
