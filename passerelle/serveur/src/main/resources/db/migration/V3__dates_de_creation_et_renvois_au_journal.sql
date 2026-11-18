-- 1. Date de création des entrées du référentiel : les listes de la console s'affichent du plus
--    récent au plus ancien. Les lignes déjà présentes prennent la date de cette migration.
ALTER TABLE membre   ADD COLUMN cree_le timestamptz NOT NULL DEFAULT now();
ALTER TABLE registre ADD COLUMN cree_le timestamptz NOT NULL DEFAULT now();
ALTER TABLE jeu      ADD COLUMN cree_le timestamptz NOT NULL DEFAULT now();
ALTER TABLE demarche ADD COLUMN cree_le timestamptz NOT NULL DEFAULT now();

CREATE INDEX membre_cree_le   ON membre (cree_le DESC);
CREATE INDEX registre_cree_le ON registre (cree_le DESC);
CREATE INDEX jeu_cree_le      ON jeu (cree_le DESC);
CREATE INDEX demarche_cree_le ON demarche (cree_le DESC);

-- 2. Renvois vers le journal. Une clé étrangère vérifie la ligne visée avec un verrou
--    « FOR KEY SHARE », qui exige le droit UPDATE sur le journal ; ce droit est retiré à tous
--    (V1), si bien qu'aucun point de contrôle ni aucun signalement ne pouvait être enregistré
--    (« permission denied for table journal »). Le contrôle passe dans un déclencheur qui lit
--    la ligne sans la verrouiller : le journal reste non modifiable par quiconque.
ALTER TABLE point_de_controle DROP CONSTRAINT point_de_controle_ligne_fkey;
ALTER TABLE signalement DROP CONSTRAINT signalement_ligne_fkey;

CREATE FUNCTION point_de_controle_ligne_existe() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM journal WHERE numero = NEW.ligne) THEN
        RAISE EXCEPTION 'ligne % absente du journal', NEW.ligne USING ERRCODE = 'foreign_key_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION signalement_ligne_existe() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM journal WHERE id = NEW.ligne) THEN
        RAISE EXCEPTION 'ligne % absente du journal', NEW.ligne USING ERRCODE = 'foreign_key_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER point_de_controle_ligne BEFORE INSERT ON point_de_controle
    FOR EACH ROW EXECUTE FUNCTION point_de_controle_ligne_existe();
CREATE TRIGGER signalement_ligne BEFORE INSERT OR UPDATE OF ligne ON signalement
    FOR EACH ROW EXECUTE FUNCTION signalement_ligne_existe();
