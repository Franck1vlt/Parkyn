-- V18 : tenant_id sur user_sessions (C3)
-- Table encore jamais remplie : colonne NOT NULL directe, sans backfill ni DEFAULT temporaire

ALTER TABLE user_sessions
    ADD COLUMN tenant_id UUID NOT NULL,
    ADD CONSTRAINT fk_user_sessions_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_user_sessions_tenant_id ON user_sessions(tenant_id);
