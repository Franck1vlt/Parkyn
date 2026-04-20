INSERT INTO users (id, first_name, last_name, email, hashed_pwd, role, is_active)
VALUES (
    gen_random_uuid(),
    'Admin',
    'Parkyn',
    'admin@parkyn.app',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVtGzOz0ke',
    'ADMIN',
    true
);