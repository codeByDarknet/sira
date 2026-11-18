-- Informations saisies par l'agent sur une pièce « autre document » (numéro de jugement, date de
-- délivrance…) : libellé, type et valeur. Elles restent dans le back-office du registre ; seules
-- les informations déclarées dans la politique du connecteur peuvent sortir vers Sira.
ALTER TABLE piece_personne ADD COLUMN informations jsonb NOT NULL DEFAULT '[]';
