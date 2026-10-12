-- V8 : tenant_id et colonnes d'audit sur les tables métier (Expand + Backfill)

-- 1. tenant_id
ALTER TABLE vehicles      ADD COLUMN tenant_id UUID;
ALTER TABLE reservations  ADD COLUMN tenant_id UUID;
ALTER TABLE notifications ADD COLUMN tenant_id UUID;

UPDATE vehicles      SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE reservations  SET tenant_id = '00000000-0000-0000-0000-000000000001';
UPDATE notifications SET tenant_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE vehicles
    ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001',
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_vehicles_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE reservations
    ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001',
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_reservations_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE notifications
    ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001',
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_notifications_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_vehicles_tenant_id      ON vehicles(tenant_id);
CREATE INDEX idx_reservations_tenant_id  ON reservations(tenant_id);
CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);

-- 2. reservations
ALTER TABLE reservations DROP CONSTRAINT reservations_agent_id_fkey;
ALTER TABLE reservations ALTER COLUMN agent_id DROP NOT NULL;
ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_agent_id
    FOREIGN KEY (agent_id) REFERENCES users(id) ON DELETE SET NULL;

-- Audit : qui a créé, qui a modifié, et quand
ALTER TABLE reservations
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ;

-- 3. Backfill de l'audit
UPDATE reservations SET created_by = agent_id;
UPDATE reservations SET updated_at = created_at;

ALTER TABLE reservations
    ALTER COLUMN updated_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET NOT NULL;
