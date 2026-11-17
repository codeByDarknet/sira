package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.jetons.Acteur;
import bf.sira.journal.Journal;
import bf.sira.journal.Trace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ce que partagent les quatre catalogues du référentiel (membres, registres, jeux, démarches) : un
 * objet se crée, se lit, se remplace et se désactive, jamais ne s'efface, et chaque écriture laisse
 * une ligne au journal.
 */
public abstract class Catalogue<T> {

    /** Ordre par défaut des listes : la dernière entrée créée en premier. */
    static final String PLUS_RECENT = "cree_le DESC, code";

    protected final JdbcClient sql;
    private final Pages pages;
    private final Journal journal;
    private final String table;
    private final String objet;
    private final RowMapper<T> lecteur;

    /**
     * @param table nom de la table, fixé par le code : il n'est jamais lu d'une entrée
     * @param objet nom de l'objet dans le journal ({@code membre}, {@code jeu}…)
     */
    protected Catalogue(
            JdbcClient sql,
            Pages pages,
            Journal journal,
            String table,
            String objet,
            RowMapper<T> lecteur) {
        this.sql = sql;
        this.pages = pages;
        this.journal = journal;
        this.table = table;
        this.objet = objet;
        this.lecteur = lecteur;
    }

    protected abstract String code(T objet);

    /**
     * Contrôle ce que la forme seule ne dit pas : références, cohérence entre champs.
     *
     * @throws SaisieRefusee si l'objet ne peut pas être enregistré
     */
    protected abstract void verifier(T objet);

    protected abstract void inserer(T objet);

    /**
     * @return le nombre de lignes remplacées
     */
    protected abstract int remplacer(T objet);

    /**
     * Refuse ce qu'un remplacement ne peut pas changer seul : ce qui relève d'une décision de
     * l'autorité, ou ce qu'une habilitation en cours a figé.
     *
     * @throws ErreurMetier 409 si le changement est refusé
     */
    protected void verifierRemplacement(T actuel, T remplacant) {}

    public Optional<T> trouver(String code) {
        return sql.sql("SELECT * FROM " + table + " WHERE code = ?")
                .param(code)
                .query(lecteur)
                .optional();
    }

    public T lire(String code) {
        return trouver(code).orElseThrow(ErreurMetier::introuvable);
    }

    @Transactional
    public T creer(T nouveau, Acteur acteur) {
        verifier(nouveau);
        try {
            inserer(nouveau);
        } catch (DuplicateKeyException e) {
            throw ErreurMetier.conflit("Ce code est déjà utilisé.");
        }
        return tracer(code(nouveau), "CREATION", acteur);
    }

    @Transactional
    public T modifier(String code, T remplacant, Acteur acteur) {
        if (!code.equals(code(remplacant))) {
            throw new SaisieRefusee("code", "Le code d'un objet ne se modifie pas.");
        }
        verifier(remplacant);
        T actuel =
                sql.sql("SELECT * FROM " + table + " WHERE code = ? FOR UPDATE")
                        .param(code)
                        .query(lecteur)
                        .optional()
                        .orElseThrow(ErreurMetier::introuvable);
        verifierRemplacement(actuel, remplacant);
        remplacer(remplacant);
        return tracer(code, "MODIFICATION", acteur);
    }

    /**
     * Termine un changement décidé par l'autorité : l'objet doit exister, et la décision est
     * journalisée sous son propre nom d'action.
     */
    protected T decide(String code, int touches, String action, Acteur acteur) {
        if (touches == 0) {
            throw ErreurMetier.introuvable();
        }
        return tracer(code, action, acteur);
    }

    /** Désactive : l'objet reste lisible, et le journal qui le cite reste compréhensible. */
    @Transactional
    public void desactiver(String code, Acteur acteur) {
        int touches =
                sql.sql("UPDATE " + table + " SET actif = false WHERE code = ?")
                        .param(code)
                        .update();
        if (touches == 0) {
            throw ErreurMetier.introuvable();
        }
        tracer(code, "DESACTIVATION", acteur);
    }

    /**
     * @param filtres condition SQL à paramètres nommés, ou {@code null}
     */
    protected Page<T> lister(String filtres, Map<String, ?> parametres, Pagination pagination) {
        return pages.lire(
                "*",
                filtres == null ? table : table + " WHERE " + filtres,
                PLUS_RECENT,
                parametres,
                pagination,
                lecteur);
    }

    /**
     * Liste avec recherche dans le texte, filtre sur l'état et tri, faits par la base sur toutes
     * les lignes, avant la pagination.
     *
     * @param filtres condition SQL propre à l'écran, ou {@code null}
     * @param texte colonnes où chercher
     * @param tris colonnes que l'écran peut trier
     */
    protected Page<T> lister(
            String filtres,
            Map<String, ?> parametres,
            Pagination pagination,
            Recherche recherche,
            List<String> texte,
            List<String> tris) {
        List<String> conditions = new ArrayList<>();
        if (filtres != null) {
            conditions.add(filtres);
        }
        conditions.add(Recherche.dansLeTexte(texte));
        conditions.add("(CAST(:actif AS boolean) IS NULL OR actif = :actif)");
        Map<String, Object> tous = new HashMap<>(parametres);
        tous.put("motif", recherche.motif());
        tous.put("actif", recherche.actifDemande());
        return pages.lire(
                "*",
                table + " WHERE " + String.join(" AND ", conditions),
                recherche.ordre(tris, PLUS_RECENT),
                tous,
                pagination,
                lecteur);
    }

    protected boolean existe(String requete, Object... parametres) {
        return sql.sql("SELECT EXISTS (" + requete + ")")
                .params(parametres)
                .query(Boolean.class)
                .single();
    }

    private T tracer(String code, String action, Acteur acteur) {
        journal.ecrire(Trace.administration(acteur, objet + ":" + code, action));
        return lire(code);
    }
}
