-- Le dossier déposé : ce que la personne a déclaré ne change plus.
ALTER TABLE dossier ADD COLUMN depose_le timestamptz;

-- Ce que la personne déclare elle-même, à côté de ce que les registres attestent : une saisie
-- libre, ou une pièce PDF. Une pièce peut remplacer un jeu que le registre n'a pas transmis
-- (refus, personne inconnue) ; elle ne remplace jamais une réponse reçue ou attendue.
CREATE TABLE declaration (
    dossier_id  uuid        NOT NULL REFERENCES dossier,
    code        text        NOT NULL,
    libelle     text        NOT NULL,
    nature      text        NOT NULL CHECK (nature IN ('TEXTE', 'PIECE')),
    -- Vrai quand la pièce remplace le jeu de registre de même code.
    remplace    boolean     NOT NULL DEFAULT false,
    valeur      text        CHECK (char_length(valeur) <= 120),
    piece_nom   text,
    piece_type  text,
    piece       bytea       CHECK (octet_length(piece) <= 2097152),
    modifiee_le timestamptz NOT NULL,
    PRIMARY KEY (dossier_id, code),
    CHECK ((nature = 'TEXTE' AND valeur IS NOT NULL AND piece IS NULL)
        OR (nature = 'PIECE' AND piece IS NOT NULL AND valeur IS NULL))
);
