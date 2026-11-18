-- Rôles des agents de l'administration et rôle de chaque agent (bf.sira.commun.agents).
-- L'agent est désigné par son identifiant de connexion dans l'annuaire, qui ne change jamais.
CREATE TABLE agent_role (
    code        text PRIMARY KEY CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    libelle     text NOT NULL UNIQUE,
    description text NOT NULL DEFAULT '',
    permissions text NOT NULL DEFAULT '',
    systeme     boolean NOT NULL DEFAULT false,
    cree_le     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE agent_affectation (
    identifiant text PRIMARY KEY,
    role        text NOT NULL REFERENCES agent_role (code) ON UPDATE CASCADE,
    modifiee_le timestamptz NOT NULL DEFAULT now()
);
