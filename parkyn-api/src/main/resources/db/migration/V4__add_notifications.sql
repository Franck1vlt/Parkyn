CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reservation_id  UUID        REFERENCES reservations(id) ON DELETE CASCADE,
    type            VARCHAR(30) NOT NULL CHECK (type IN ('NEW_RESERVATION','RESERVATION_ENDING_SOON','VEHICLE_MAINTENANCE')),
    is_read         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
