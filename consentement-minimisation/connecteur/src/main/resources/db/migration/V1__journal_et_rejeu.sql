-- Journal local du connecteur : qui a demandé quoi, quand, avec quelle issue. Jamais la valeur.
CREATE TABLE journal (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    horodatage   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    id_echange   VARCHAR(128) NOT NULL,
    demandeur    VARCHAR(128) NOT NULL,
    demarche     VARCHAR(64)  NOT NULL,
    finalite     VARCHAR(500) NOT NULL,
    jeu          VARCHAR(128) NOT NULL,
    niveau       VARCHAR(16)  NOT NULL,
    objet        VARCHAR(512),
    pseudonyme   VARCHAR(128) NOT NULL,
    autorisation VARCHAR(64)  NOT NULL,
    canal        VARCHAR(32),
    resultat     VARCHAR(32)  NOT NULL
);

CREATE INDEX journal_horodatage ON journal (horodatage);

-- Ajout seul : une ligne écrite ne se corrige pas et ne s'efface pas.
CREATE FUNCTION journal_ajout_seul() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'le journal du connecteur est en ajout seul';
END;
$$;

CREATE TRIGGER journal_sans_modification
    BEFORE UPDATE OR DELETE ON journal
    FOR EACH ROW EXECUTE FUNCTION journal_ajout_seul();

CREATE TRIGGER journal_sans_vidage
    BEFORE TRUNCATE ON journal
    FOR EACH STATEMENT EXECUTE FUNCTION journal_ajout_seul();

-- Requêtes déjà reçues, le temps de leur validité (anti-rejeu).
CREATE TABLE jti_vu (
    cle       VARCHAR(300) PRIMARY KEY,
    expire_le TIMESTAMPTZ  NOT NULL
);

CREATE INDEX jti_vu_expire_le ON jti_vu (expire_le);
