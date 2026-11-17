-- Référentiel : qui est raccordé, ce que chaque registre fournit, qui a le droit de demander quoi.

CREATE TABLE membre (
    code              text PRIMARY KEY CHECK (code ~ '^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$'),
    nom               text NOT NULL,
    organisme         text NOT NULL,
    est_service       boolean NOT NULL,
    est_registre      boolean NOT NULL,
    retours_autorises text[] NOT NULL DEFAULT '{}',
    actif             boolean NOT NULL DEFAULT true,
    CHECK (est_service OR est_registre)
);

CREATE TABLE registre (
    code              text PRIMARY KEY CHECK (code ~ '^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$'),
    nom               text NOT NULL,
    membre            text NOT NULL REFERENCES membre,
    adresse           text NOT NULL,
    transport         text NOT NULL,
    actif             boolean NOT NULL DEFAULT true,
    dernier_succes_le timestamptz,
    dernier_echec_le  timestamptz
);

CREATE TABLE jeu (
    code        text PRIMARY KEY,
    registre    text NOT NULL REFERENCES registre,
    niveau      text NOT NULL CHECK (niveau IN ('STATUT', 'CHAMPS', 'DOCUMENT')),
    libelle     text NOT NULL,
    description text,
    validite    text NOT NULL,
    regle       jsonb,
    champs      text[],
    document    text,
    actif       boolean NOT NULL DEFAULT true
);

CREATE TABLE demarche (
    code                     text PRIMARY KEY,
    libelle                  text NOT NULL,
    finalite                 text NOT NULL,
    base_legale              text,
    service                  text NOT NULL REFERENCES membre,
    duree_autorisation_jours integer NOT NULL CHECK (duree_autorisation_jours >= 0),
    actif                    boolean NOT NULL DEFAULT true
);

CREATE TABLE habilitation (
    id            uuid PRIMARY KEY,
    service       text NOT NULL REFERENCES membre,
    demarche      text NOT NULL REFERENCES demarche,
    etat          text NOT NULL CHECK (etat IN ('BROUILLON', 'SOUMISE', 'APPROUVEE', 'REJETEE', 'SUSPENDUE')),
    justification text NOT NULL,
    motif         text,
    instruite_par text NOT NULL,
    -- Tous ceux qui ont préparé le dossier (identifiants de compte) : aucun d'eux ne peut le décider.
    instructeurs  text[] NOT NULL,
    decidee_par   text,
    decidee_le    timestamptz,
    creee_le      timestamptz NOT NULL
);

-- Une seule habilitation en vigueur par service et par démarche.
CREATE UNIQUE INDEX habilitation_en_vigueur ON habilitation (service, demarche) WHERE etat = 'APPROUVEE';

CREATE TABLE habilitation_jeu (
    habilitation uuid NOT NULL REFERENCES habilitation,
    jeu          text NOT NULL REFERENCES jeu,
    PRIMARY KEY (habilitation, jeu)
);

-- Citoyens : seul endroit où l'identifiant unique est conservé. Partout ailleurs, le citoyen est
-- désigné par un pseudonyme (interne ici, propre à chaque service, ou propre au journal).

CREATE TABLE citoyen (
    id                 text PRIMARY KEY,
    identifiant_unique text NOT NULL UNIQUE
);

CREATE TABLE pseudonyme_service (
    service    text NOT NULL REFERENCES membre,
    pseudonyme text NOT NULL,
    citoyen    text NOT NULL REFERENCES citoyen,
    PRIMARY KEY (service, pseudonyme)
);

CREATE TABLE autorisation (
    id          uuid PRIMARY KEY,
    citoyen     text NOT NULL REFERENCES citoyen,
    service     text NOT NULL REFERENCES membre,
    demarche    text NOT NULL REFERENCES demarche,
    jeux        text[] NOT NULL,
    canal       text NOT NULL,
    accordee_le timestamptz NOT NULL,
    expire_le   timestamptz NOT NULL,
    retiree_le  timestamptz
);

CREATE INDEX autorisation_citoyen ON autorisation (citoyen, accordee_le DESC);

CREATE TABLE demande (
    id                     uuid PRIMARY KEY,
    service                text NOT NULL REFERENCES membre,
    demarche               text NOT NULL REFERENCES demarche,
    jeux                   text[] NOT NULL,
    canal                  text NOT NULL CHECK (canal IN ('REDIRECTION', 'CODE', 'NOTIFICATION')),
    etat                   text NOT NULL CHECK (etat IN
                               ('EN_ATTENTE_ACCORD', 'REFUSEE', 'EXPIREE', 'ANNULEE', 'EN_COURS', 'PARTIELLE', 'COMPLETE')),
    citoyen                text REFERENCES citoyen,
    autorisation           uuid REFERENCES autorisation,
    retour_url             text,
    -- Les codes ne vivent que le temps de l'attente : ils sont effacés à la décision.
    code_court             text,
    code_court_empreinte   text,
    code_verification      text,
    code_retour_empreinte  text,
    certificat_chiffrement text NOT NULL,
    cle_idempotence        text NOT NULL,
    empreinte_requete      text NOT NULL,
    creee_le               timestamptz NOT NULL,
    expire_le              timestamptz NOT NULL,
    decidee_le             timestamptz,
    UNIQUE (service, cle_idempotence)
);

CREATE UNIQUE INDEX demande_code_court ON demande (code_court_empreinte) WHERE code_court_empreinte IS NOT NULL;
CREATE INDEX demande_citoyen ON demande (citoyen, creee_le DESC);
CREATE INDEX demande_en_attente ON demande (expire_le) WHERE etat = 'EN_ATTENTE_ACCORD';

-- Codes courts refusés, pour borner les essais d'un même citoyen (contre la recherche exhaustive).
CREATE TABLE essai_rattachement (
    citoyen text NOT NULL REFERENCES citoyen,
    quand   timestamptz NOT NULL
);

CREATE INDEX essai_rattachement_citoyen ON essai_rattachement (citoyen, quand);

-- Dépôt des attestations : JWE opaques, chiffrés pour le service. La passerelle ne peut pas les lire.
CREATE TABLE attestation (
    id                     uuid PRIMARY KEY,
    service                text NOT NULL REFERENCES membre,
    citoyen                text NOT NULL REFERENCES citoyen,
    demarche               text NOT NULL REFERENCES demarche,
    jeu                    text NOT NULL REFERENCES jeu,
    -- Une attestation n'est réutilisable que par la clé pour laquelle elle a été chiffrée.
    empreinte_certificat   text NOT NULL,
    jwe                    text NOT NULL,
    id_echange             text NOT NULL,
    obtenue_le             timestamptz NOT NULL,
    expire_le              timestamptz NOT NULL
);

CREATE INDEX attestation_reutilisation ON attestation (service, citoyen, demarche, jeu, expire_le);

-- Un élément = un jeu attendu pour une demande. Les éléments A_TRAITER forment la file d'attente.
CREATE TABLE element (
    id                     uuid PRIMARY KEY,
    demande                uuid NOT NULL REFERENCES demande,
    jeu                    text NOT NULL REFERENCES jeu,
    registre               text NOT NULL REFERENCES registre,
    etat                   text NOT NULL CHECK (etat IN ('A_TRAITER', 'OBTENU', 'REUTILISE', 'REFUSE', 'INCONNU', 'EXPIRE')),
    attestation            uuid REFERENCES attestation,
    tentatives             integer NOT NULL DEFAULT 0,
    prochaine_tentative_le timestamptz NOT NULL,
    -- Bail : l'instance qui traite l'élément le réserve jusqu'à cet instant ; si elle s'arrête, il redevient disponible.
    bail_jusqu_a           timestamptz,
    echec_journalise       boolean NOT NULL DEFAULT false,
    cree_le                timestamptz NOT NULL,
    UNIQUE (demande, jeu)
);

CREATE INDEX element_file ON element (prochaine_tentative_le) WHERE etat = 'A_TRAITER';
CREATE INDEX element_attestation ON element (attestation) WHERE attestation IS NOT NULL;

-- Journal : ajout seul, chaîné. hachage = SHA-256(hachage_precedent ‖ contenu canonique).
CREATE TABLE journal (
    numero            bigint PRIMARY KEY,
    id                uuid NOT NULL UNIQUE,
    horodatage        timestamptz NOT NULL,
    type              text NOT NULL,
    resultat          text NOT NULL,
    citoyen           text,
    service           text,
    registre          text,
    jeu               text,
    niveau            text,
    demarche          text,
    finalite          text,
    canal             text,
    id_echange        text,
    demande           uuid,
    autorisation      uuid,
    detail            text,
    acteur            text,
    objet             text,
    hachage_precedent bytea NOT NULL,
    hachage           bytea NOT NULL
);

CREATE INDEX journal_citoyen ON journal (citoyen, numero DESC) WHERE citoyen IS NOT NULL;
CREATE INDEX journal_autorisation ON journal (autorisation) WHERE autorisation IS NOT NULL;
CREATE INDEX journal_horodatage ON journal (horodatage);

CREATE FUNCTION journal_immuable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'le journal est en ajout seul' USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER journal_sans_modification BEFORE UPDATE OR DELETE ON journal
    FOR EACH ROW EXECUTE FUNCTION journal_immuable();
CREATE TRIGGER journal_sans_troncature BEFORE TRUNCATE ON journal
    FOR EACH STATEMENT EXECUTE FUNCTION journal_immuable();

-- Le rôle de la passerelle se retire à lui-même le droit de modifier le journal.
REVOKE UPDATE, DELETE, TRUNCATE ON journal FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON journal FROM CURRENT_USER;

CREATE TABLE point_de_controle (
    id         uuid PRIMARY KEY,
    ligne      bigint NOT NULL REFERENCES journal,
    horodatage timestamptz NOT NULL,
    jws        text NOT NULL
);

CREATE INDEX point_de_controle_ligne ON point_de_controle (ligne);

CREATE TRIGGER point_de_controle_sans_modification BEFORE UPDATE OR DELETE ON point_de_controle
    FOR EACH ROW EXECUTE FUNCTION journal_immuable();

-- Le motif d'un signalement est un texte libre du citoyen : il reste hors du journal.
CREATE TABLE signalement (
    id         uuid PRIMARY KEY,
    ligne      uuid NOT NULL UNIQUE REFERENCES journal (id),
    motif      text NOT NULL,
    cree_le    timestamptz NOT NULL,
    reponse    text,
    traite_par text,
    traite_le  timestamptz
);
