-- Identifiant technique de chaque fiche : c'est lui qui paraît dans les adresses du back-office.
-- L'identifiant unique (douze chiffres) reste la donnée métier, lue par l'API interne.
ALTER TABLE personne ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE personne ADD CONSTRAINT personne_id_unique UNIQUE (id);

-- Identifiant technique des rôles, pour les adresses de l'API.
ALTER TABLE agent_role ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE agent_role ADD CONSTRAINT agent_role_id_unique UNIQUE (id);

-- Pièces déposées par un agent pour une personne : le fichier, son type, et les informations
-- qu'il contient. Une pièce PDF d'un type que délivre le registre remplace le document produit.
CREATE TABLE piece_personne (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    personne    uuid        NOT NULL REFERENCES personne (id) ON DELETE CASCADE,
    type        text        NOT NULL CHECK (type ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    titre       text        NOT NULL,
    champs      text        NOT NULL DEFAULT '',
    nom_fichier text        NOT NULL,
    type_mime   text        NOT NULL CHECK (type_mime IN ('application/pdf', 'image/png', 'image/jpeg')),
    taille      integer     NOT NULL,
    contenu     bytea       NOT NULL,
    depose_par  text        NOT NULL,
    depose_le   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX piece_personne_ordre ON piece_personne (personne, type, depose_le DESC);
