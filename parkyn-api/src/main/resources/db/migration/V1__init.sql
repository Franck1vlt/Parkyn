CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name   VARCHAR(80)  NOT NULL,
    last_name    VARCHAR(80)  NOT NULL,
    email        VARCHAR(255) UNIQUE,
    hashed_pwd   VARCHAR,
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','AGENT','DRIVER')),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number     VARCHAR(20)  NOT NULL UNIQUE,
    brand            VARCHAR(80)  NOT NULL,
    model            VARCHAR(80)  NOT NULL,
    year             INTEGER,
    mileage_km       INTEGER      NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE'
                         CHECK (status IN ('AVAILABLE','RESERVED','MAINTENANCE','RETIRED')),
    commissioned_at  DATE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE reservations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id       UUID        NOT NULL REFERENCES vehicles(id),
    driver_id        UUID        NOT NULL REFERENCES users(id),
    agent_id         UUID        NOT NULL REFERENCES users(id),
    start_date       TIMESTAMPTZ NOT NULL,
    end_date         TIMESTAMPTZ NOT NULL,
    returned_at      TIMESTAMPTZ,
    condition_before VARCHAR(20) NOT NULL CHECK (condition_before IN ('EXCELLENT','GOOD','FAIR','POOR')),
    mileage_start    INTEGER     NOT NULL,
    notes_before     TEXT,
    condition_after  VARCHAR(20) CHECK (condition_after IN ('EXCELLENT','GOOD','FAIR','POOR')),
    mileage_end      INTEGER,
    damage_reported  BOOLEAN,
    damage_details   TEXT,
    notes_after      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR     NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index utiles pour les requêtes fréquentes
CREATE INDEX idx_reservations_vehicle   ON reservations(vehicle_id);
CREATE INDEX idx_reservations_driver    ON reservations(driver_id);
CREATE INDEX idx_vehicles_status        ON vehicles(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);