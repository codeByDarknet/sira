-- Accès des personnes (revue de sécurité) : codes conservés par HMAC seulement, code de vérification
-- tapé par le citoyen, et demande par notification adressée à un identifiant sans créer de citoyen.

-- Une demande encore en attente a été créée avec des codes en clair : elle expire maintenant.
UPDATE demande SET expire_le = now() WHERE etat = 'EN_ATTENTE_ACCORD' AND expire_le > now();

ALTER TABLE demande DROP COLUMN code_court;
UPDATE demande SET code_court_empreinte = NULL, code_verification = NULL;
ALTER TABLE demande RENAME COLUMN code_verification TO code_verification_empreinte;
-- Codes de vérification erronés tapés pour cette demande.
ALTER TABLE demande ADD COLUMN essais_verification integer NOT NULL DEFAULT 0;
-- Canal NOTIFICATION par identifiant unique : le pseudonyme interne de la personne visée, tant
-- qu'elle ne s'est pas connectée. Aucune ligne citoyen n'est créée pour un identifiant tapé.
ALTER TABLE demande ADD COLUMN designation text;
ALTER TABLE demande ADD CONSTRAINT demande_designee_une_fois CHECK (citoyen IS NULL OR designation IS NULL);

CREATE INDEX demande_designation ON demande (designation) WHERE designation IS NOT NULL;
