-- Identifiant des appels reçus en UUID, comme toutes les tables. L'ordre se lit sur l'horodatage.
ALTER TABLE appel_recu ALTER COLUMN id DROP IDENTITY;
ALTER TABLE appel_recu ALTER COLUMN id TYPE uuid USING gen_random_uuid();
ALTER TABLE appel_recu ALTER COLUMN id SET DEFAULT gen_random_uuid();
CREATE INDEX appel_recu_ordre ON appel_recu (horodatage DESC);
