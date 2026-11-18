CREATE SEQUENCE numero_dossier;

-- Un dossier par citoyen, désigné par son pseudonyme chez ce service : jamais l'identifiant unique.
CREATE TABLE dossier (
    id         uuid PRIMARY KEY,
    numero     text        NOT NULL UNIQUE,
    pseudonyme text        NOT NULL UNIQUE,
    etat       text        NOT NULL CHECK (etat IN ('EN_VERIFICATION', 'COMPLET', 'A_EXAMINER')),
    cree_le    timestamptz NOT NULL,
    modifie_le timestamptz NOT NULL
);

CREATE INDEX dossier_ordre ON dossier (modifie_le DESC, id);

-- Les demandes ouvertes auprès de Sira. Avant l'accord, la ligne ne désigne personne.
CREATE TABLE demande (
    id          uuid PRIMARY KEY,
    canal       text        NOT NULL CHECK (canal IN ('REDIRECTION', 'CODE', 'NOTIFICATION')),
    origine     text        NOT NULL CHECK (origine IN ('CITOYEN', 'AGENT')),
    etat        text        NOT NULL CHECK (etat IN ('EN_ATTENTE_ACCORD', 'REFUSEE', 'EXPIREE',
                                                     'ANNULEE', 'EN_COURS', 'PARTIELLE', 'COMPLETE')),
    jeux        jsonb       NOT NULL,
    -- Gardé le temps que les registres en panne répondent : sans lui la passerelle ne remet rien.
    code_retour text,
    dossier_id  uuid REFERENCES dossier,
    creee_le    timestamptz NOT NULL,
    expire_le   timestamptz NOT NULL
);

CREATE INDEX demande_a_suivre ON demande (creee_le)
    WHERE etat IN ('EN_ATTENTE_ACCORD', 'EN_COURS', 'PARTIELLE');
CREATE INDEX demande_du_dossier ON demande (dossier_id, creee_le DESC);

-- Ce que les registres ont attesté, tel que reçu : une ligne par jeu de données du dossier.
CREATE TABLE verification (
    dossier_id    uuid        NOT NULL REFERENCES dossier,
    jeu           text        NOT NULL,
    libelle       text        NOT NULL,
    niveau        text        NOT NULL CHECK (niveau IN ('STATUT', 'CHAMPS', 'DOCUMENT')),
    registre      text        NOT NULL,
    etat          text        NOT NULL CHECK (etat IN ('A_TRAITER', 'OBTENU', 'REUTILISE', 'REFUSE',
                                                       'INCONNU', 'EXPIRE')),
    -- Identifiant de l'attestation, gardé comme preuve de l'échange.
    id_echange    text,
    valeur        boolean,
    champs        jsonb,
    document_nom  text,
    document_type text,
    document      bytea,
    modifiee_le   timestamptz NOT NULL,
    PRIMARY KEY (dossier_id, jeu)
);
