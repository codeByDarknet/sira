-- Identifiant de chaque ligne du journal en UUID, comme toutes les tables de Sira. L'ordre des
-- lignes se lit sur l'horodatage. Le changement de type ne passe pas par un UPDATE : le déclencheur
-- d'ajout seul reste en place et n'est pas sollicité.
ALTER TABLE journal ALTER COLUMN id DROP IDENTITY;
ALTER TABLE journal ALTER COLUMN id TYPE uuid USING gen_random_uuid();
ALTER TABLE journal ALTER COLUMN id SET DEFAULT gen_random_uuid();
