ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_plate_number_key;
DROP INDEX IF EXISTS vehicles_plate_number_key;

CREATE UNIQUE INDEX IF NOT EXISTS vehicles_plate_number_active_idx
    ON vehicles (plate_number)
    WHERE deleted_at IS NULL;
