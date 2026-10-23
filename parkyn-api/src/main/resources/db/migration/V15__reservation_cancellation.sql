-- V15 : annulation des réservations (annulation douce : la réservation reste dans l'historique)

ALTER TABLE reservations
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- Une réservation annulée ne bloque plus son créneau : la contrainte de la V14 ne porte plus que
-- sur les réservations non annulées (même règle que ReservationRepository.findVehicleIdsBookedBetween)
ALTER TABLE reservations DROP CONSTRAINT reservations_no_overlap;

ALTER TABLE reservations
    ADD CONSTRAINT reservations_no_overlap
    EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(start_date, GREATEST(start_date, LEAST(end_date, COALESCE(returned_at, end_date)))) WITH &&
    )
    WHERE (cancelled_at IS NULL);

-- Notification envoyée aux agents et administrateurs quand une réservation est annulée
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('NEW_RESERVATION', 'RESERVATION_ENDING_SOON', 'VEHICLE_MAINTENANCE', 'RESERVATION_CANCELLED'));
