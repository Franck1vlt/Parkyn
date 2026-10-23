-- V16 : notification d'une réservation en attente (véhicule précédent pas encore retourné)

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('NEW_RESERVATION', 'RESERVATION_ENDING_SOON', 'VEHICLE_MAINTENANCE',
                    'RESERVATION_CANCELLED', 'RESERVATION_ON_HOLD'));
