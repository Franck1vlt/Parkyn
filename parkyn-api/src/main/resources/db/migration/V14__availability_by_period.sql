-- V14 : disponibilité des véhicules calculée à partir des périodes réservées

-- 1. RESERVED n'est plus stocké : l'API le calcule à partir des réservations.
--    La valeur reste autorisée par la contrainte CHECK jusqu'au Contract (le frontend l'affiche encore).
UPDATE vehicles SET status = 'AVAILABLE' WHERE status = 'RESERVED';

-- 2. Deux réservations d'un même véhicule ne peuvent pas se chevaucher : filet de sécurité des demandes
--    simultanées (l'application les traite déjà l'une après l'autre, avec un verrou sur le véhicule).
--    Période occupée : [début, fin[, ou [début, retour[ si le véhicule est rendu en avance.
--    Même règle que ReservationRepository.findVehicleIdsBookedBetween.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Des chevauchements déjà en base empêcheraient la contrainte : la migration échoue avec leur liste,
-- sans rien modifier. Les corriger à la main, puis relancer.
DO $$
DECLARE
    conflicts TEXT;
BEGIN
    SELECT string_agg(a.id || ' / ' || b.id, ', ') INTO conflicts
    FROM reservations a
    JOIN reservations b ON a.vehicle_id = b.vehicle_id AND a.id < b.id
    WHERE tstzrange(a.start_date, GREATEST(a.start_date, LEAST(a.end_date, COALESCE(a.returned_at, a.end_date))))
       && tstzrange(b.start_date, GREATEST(b.start_date, LEAST(b.end_date, COALESCE(b.returned_at, b.end_date))));

    IF conflicts IS NOT NULL THEN
        RAISE EXCEPTION 'V14 : réservations qui se chevauchent, à corriger avant la migration : %', conflicts;
    END IF;
END $$;

ALTER TABLE reservations
    ADD CONSTRAINT reservations_no_overlap
    EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(start_date, GREATEST(start_date, LEAST(end_date, COALESCE(returned_at, end_date)))) WITH &&
    );
