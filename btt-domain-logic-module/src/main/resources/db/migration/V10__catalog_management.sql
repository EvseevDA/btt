INSERT INTO role_permission (role_id, permission)
SELECT id, 'catalog.routes.create' FROM role WHERE name = 'PORTAL_ADMIN';

INSERT INTO transport_kind (id, name)
VALUES
    ('a29025b6-4d2a-4cd1-adcc-510d93168df7', 'Автобус'),
    ('2bb18380-60a3-4ba3-bca6-f30f5e9eef46', 'Поезд'),
    ('8b741d14-f9ed-47cd-b7dc-8ee766db94c5', 'Самолёт'),
    ('ef67cc54-5625-4e90-a7e5-8d7cb4639a16', 'Водный транспорт')
ON CONFLICT DO NOTHING;
