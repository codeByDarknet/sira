-- Identifiant technique des rôles, pour les adresses de l'API.
ALTER TABLE agent_role ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE agent_role ADD CONSTRAINT agent_role_id_unique UNIQUE (id);
