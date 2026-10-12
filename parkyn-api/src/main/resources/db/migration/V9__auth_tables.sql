-- V9 : tables d'authentification (Expand). Aucune donnée.

CREATE TABLE verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_verification_tokens_dates CHECK (expires_at > created_at)
);

CREATE INDEX idx_verification_tokens_user_id    ON verification_tokens(user_id);
CREATE INDEX idx_verification_tokens_expires_at ON verification_tokens(expires_at);

CREATE TABLE user_sessions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id          UUID        NOT NULL,
    user_agent         VARCHAR(255),
    ip                 VARCHAR(45),
    last_used_at       TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_sessions_dates CHECK (expires_at > created_at)
);

CREATE INDEX idx_user_sessions_user_id_revoked_at ON user_sessions(user_id, revoked_at);
CREATE INDEX idx_user_sessions_family_id          ON user_sessions(family_id);
