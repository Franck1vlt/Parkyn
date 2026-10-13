-- Normaliser les emails déjà présents dans la base
UPDATE users
SET email = LOWER(TRIM(email)) 
WHERE email <> LOWER(TRIM(email));