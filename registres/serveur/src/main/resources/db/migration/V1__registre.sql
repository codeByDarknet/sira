-- Le « système ancien » d'une administration : une fiche par personne, telle quelle.
CREATE TABLE personne (
    identifiant text PRIMARY KEY CHECK (identifiant ~ '^[0-9]{12}$'),
    fiche       jsonb       NOT NULL,
    -- Nom, prénoms et identifiant sans accents ni majuscules, pour la recherche du back-office.
    recherche   text        NOT NULL,
    modifiee_le timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX personne_ordre ON personne ((fiche ->> 'nom'), (fiche ->> 'prenoms'), identifiant);

-- Panneau de pannes : une seule ligne, l'état courant de l'API interne.
CREATE TABLE panne (
    unique_ligne boolean PRIMARY KEY DEFAULT true CHECK (unique_ligne),
    mode         text        NOT NULL CHECK (mode IN ('NORMAL', 'LENT', 'ERREURS', 'MUET')),
    delai_ms     integer     NOT NULL CHECK (delai_ms BETWEEN 100 AND 30000),
    modifiee_le  timestamptz NOT NULL DEFAULT now()
);

INSERT INTO panne (mode, delai_ms) VALUES ('NORMAL', 5000);

-- Appels reçus par l'API interne : qui appelle, quand, pour quoi. Jamais la personne concernée.
CREATE TABLE appel_recu (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    horodatage timestamptz NOT NULL DEFAULT now(),
    type       text        NOT NULL CHECK (type IN ('FICHE', 'DOCUMENT')),
    mode       text        NOT NULL CHECK (mode IN ('NORMAL', 'LENT', 'ERREURS', 'MUET')),
    -- Absent quand le registre n'a pas répondu (mode MUET).
    statut     integer,
    duree_ms   integer     NOT NULL,
    origine    text        NOT NULL
);
