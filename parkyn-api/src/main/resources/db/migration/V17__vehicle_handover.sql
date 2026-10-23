-- V17 : remise du véhicule (état des lieux de départ et remise des clés, décisions H1 à H5)

ALTER TABLE reservations
    ADD COLUMN handed_over_at TIMESTAMPTZ,
    ADD COLUMN handed_over_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- Le kilométrage de départ est saisi à la remise des clés, plus à la création
ALTER TABLE reservations ALTER COLUMN mileage_start DROP NOT NULL;

-- H5 : avant la V17, la remise n'était pas enregistrée. Une réservation retournée, ou commencée sans
-- réservation antérieure du même véhicule encore dehors, est considérée comme remise à son début (au plus
-- tard à son retour). Une réservation qui attendait le véhicule (retard du précédent) n'a pas été remise.
UPDATE reservations r
SET handed_over_at = LEAST(r.start_date, COALESCE(r.returned_at, r.start_date))
WHERE r.cancelled_at IS NULL
  AND (
      r.returned_at IS NOT NULL
      OR (
          r.start_date <= NOW()
          AND NOT EXISTS (
              SELECT 1 FROM reservations p
              WHERE p.vehicle_id = r.vehicle_id
                AND p.id <> r.id
                AND p.returned_at IS NULL
                AND p.cancelled_at IS NULL
                AND p.start_date < r.start_date
          )
      )
  );

-- H3 : la remise est faite par un agent ou un administrateur, comme le retour
INSERT INTO permissions (code, scope, description) VALUES
    ('reservation:handover:all', 'TENANT', 'Remettre le véhicule (état des lieux de départ et remise des clés)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'reservation:handover:all'
WHERE r.tenant_id IS NULL
  AND r.name IN ('ADMIN', 'AGENT');
