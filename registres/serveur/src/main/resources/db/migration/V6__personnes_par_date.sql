-- La liste des personnes du back-office s'affiche de la plus récemment ajoutée à la plus ancienne.
-- Les personnes déjà présentes prennent la date de cette migration (puis l'ordre alphabétique).
ALTER TABLE personne ADD COLUMN creee_le timestamptz NOT NULL DEFAULT now();
CREATE INDEX personne_creee_le ON personne (creee_le DESC);
