package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.jetons.Acteur;
import bf.sira.journal.Journal;
import bf.sira.journal.Trace;
import bf.sira.passerelle.config.Erreurs;
import bf.sira.passerelle.referentiel.Habilitation.Etat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le cycle d'une habilitation : l'instructeur la prépare et la soumet, l'autorité la décide. Celui
 * qui a préparé un dossier ne peut pas le décider.
 */
@Component
public class Habilitations {

    private static final String COLONNES =
            "h.*, ARRAY(SELECT jeu FROM habilitation_jeu WHERE habilitation = h.id ORDER BY jeu)"
                    + " AS jeux";

    private final JdbcClient sql;
    private final Pages pages;
    private final Journal journal;
    private final Clock horloge;

    Habilitations(JdbcClient sql, Pages pages, Journal journal, Clock horloge) {
        this.sql = sql;
        this.pages = pages;
        this.journal = journal;
        this.horloge = horloge;
    }

    public Page<Habilitation> lister(String etat, Pagination pagination, Recherche recherche) {
        return pages.lire(
                COLONNES,
                "habilitation h WHERE (CAST(:etat AS text) IS NULL OR h.etat = :etat) AND "
                        + Recherche.dansLeTexte(
                                List.of(
                                        "h.service",
                                        "h.demarche",
                                        "h.instruite_par",
                                        "h.justification")),
                recherche.ordre(
                        List.of("service", "demarche", "etat", "instruite_par"),
                        "h.creee_le DESC, h.id"),
                Sql.parametres("etat", Sql.filtre(etat), "motif", recherche.motif()),
                pagination,
                (ligne, i) -> lire(ligne));
    }

    public Habilitation lire(UUID id) {
        return sql.sql("SELECT " + COLONNES + " FROM habilitation h WHERE h.id = ?")
                .param(id)
                .query((ligne, i) -> lire(ligne))
                .optional()
                .orElseThrow(ErreurMetier::introuvable);
    }

    /**
     * Les jeux qu'un service peut demander pour une démarche : ceux de son habilitation approuvée,
     * tant que le service, la démarche, les jeux et leurs registres sont actifs.
     *
     * @return les codes des jeux ; vide s'il n'y a pas d'habilitation en vigueur
     */
    public List<String> jeuxHabilites(String service, String demarche) {
        return sql.sql(
                        "SELECT hj.jeu FROM habilitation h"
                                + " JOIN membre m ON m.code = h.service AND m.actif AND m.est_service"
                                + " JOIN demarche d ON d.code = h.demarche AND d.actif"
                                + " AND d.service = h.service"
                                + " JOIN habilitation_jeu hj ON hj.habilitation = h.id"
                                + " JOIN jeu j ON j.code = hj.jeu AND j.actif"
                                + " JOIN registre r ON r.code = j.registre AND r.actif"
                                + " WHERE h.service = ? AND h.demarche = ? AND h.etat = 'APPROUVEE'"
                                + " ORDER BY hj.jeu")
                .params(service, demarche)
                .query(String.class)
                .list();
    }

    @Transactional
    public Habilitation creer(SaisieHabilitation saisie, Acteur acteur) {
        return tracer(inserer(saisie, Etat.BROUILLON, acteur), "CREATION", acteur);
    }

    /**
     * Enregistre une habilitation déjà en vigueur. Réservé à l'amorçage d'une base vide : aucune
     * route n'y mène.
     */
    @Transactional
    public Habilitation amorcer(SaisieHabilitation saisie) {
        UUID id = inserer(saisie, Etat.APPROUVEE, Acteur.AMORCAGE);
        sql.sql("UPDATE habilitation SET decidee_par = ?, decidee_le = creee_le WHERE id = ?")
                .params(Acteur.AMORCAGE.nom(), id)
                .update();
        return tracer(id, "APPROBATION", Acteur.AMORCAGE);
    }

    private UUID inserer(SaisieHabilitation saisie, Etat etat, Acteur acteur) {
        verifier(saisie);
        UUID id = UUID.randomUUID();
        sql.sql(
                        "INSERT INTO habilitation (id, service, demarche, etat, justification,"
                                + " instruite_par, instructeurs, creee_le)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        id,
                        saisie.service(),
                        saisie.demarche(),
                        etat.name(),
                        saisie.justification(),
                        acteur.nom(),
                        new String[] {acteur.id()},
                        Sql.date(horloge.instant()))
                .update();
        enregistrerJeux(id, saisie.jeux());
        return id;
    }

    /** Seul un brouillon se modifie : ce qui a été soumis est ce qui sera décidé. */
    @Transactional
    public Habilitation modifier(UUID id, SaisieHabilitation saisie, Acteur acteur) {
        verifier(saisie);
        int modifiees =
                sql.sql(
                                "UPDATE habilitation SET service = ?, demarche = ?,"
                                        + " justification = ?, instructeurs = ARRAY(SELECT DISTINCT"
                                        + " unnest(instructeurs || ?::text)) WHERE id = ?"
                                        + " AND etat = 'BROUILLON'")
                        .params(
                                saisie.service(),
                                saisie.demarche(),
                                saisie.justification(),
                                acteur.id(),
                                id)
                        .update();
        if (modifiees == 0) {
            throw refus(id, "Seul un brouillon peut être modifié.");
        }
        sql.sql("DELETE FROM habilitation_jeu WHERE habilitation = ?").param(id).update();
        enregistrerJeux(id, saisie.jeux());
        return tracer(id, "MODIFICATION", acteur);
    }

    @Transactional
    public Habilitation soumettre(UUID id, Acteur acteur) {
        int soumises =
                sql.sql(
                                "UPDATE habilitation SET etat = 'SOUMISE', instructeurs ="
                                        + " ARRAY(SELECT DISTINCT unnest(instructeurs || ?::text))"
                                        + " WHERE id = ? AND etat = 'BROUILLON'")
                        .params(acteur.id(), id)
                        .update();
        if (soumises == 0) {
            throw refus(id, "Seul un brouillon peut être soumis.");
        }
        return tracer(id, "SOUMISSION", acteur);
    }

    /**
     * Met l'habilitation en vigueur. Une habilitation suspendue peut être rétablie de la même
     * façon.
     */
    @Transactional
    public Habilitation approuver(UUID id, Acteur acteur) {
        try {
            decider(
                    id,
                    Etat.APPROUVEE,
                    Set.of(Etat.SOUMISE, Etat.SUSPENDUE),
                    null,
                    acteur,
                    "Seule une habilitation soumise ou suspendue peut être approuvée.");
        } catch (DuplicateKeyException e) {
            throw ErreurMetier.conflit(
                    "Une autre habilitation est déjà en vigueur pour ce service et cette"
                            + " démarche. Suspendez-la d'abord.");
        }
        return tracer(id, "APPROBATION", acteur);
    }

    @Transactional
    public Habilitation rejeter(UUID id, String motif, Acteur acteur) {
        decider(
                id,
                Etat.REJETEE,
                Set.of(Etat.SOUMISE),
                motif,
                acteur,
                "Seule une habilitation soumise peut être rejetée.");
        return tracer(id, "REJET", acteur);
    }

    /** Effet immédiat : le service ne peut plus créer de demande pour cette démarche. */
    @Transactional
    public Habilitation suspendre(UUID id, String motif, Acteur acteur) {
        decider(
                id,
                Etat.SUSPENDUE,
                Set.of(Etat.APPROUVEE),
                motif,
                acteur,
                "Seule une habilitation approuvée peut être suspendue.");
        return tracer(id, "SUSPENSION", acteur);
    }

    /** L'instructeur retire un dossier qui n'a pas encore été décidé ; rien n'est effacé. */
    @Transactional
    public void retirer(UUID id, Acteur acteur) {
        int retirees =
                sql.sql(
                                "UPDATE habilitation SET etat = 'REJETEE', motif = ?"
                                        + " WHERE id = ? AND etat IN ('BROUILLON', 'SOUMISE')")
                        .params("Retirée par l'instructeur.", id)
                        .update();
        if (retirees == 0) {
            throw refus(id, "Une habilitation décidée ne se retire pas : suspendez-la.");
        }
        tracer(id, "RETRAIT", acteur);
    }

    private void decider(
            UUID id, Etat nouvel, Set<Etat> depuis, String motif, Acteur acteur, String sinon) {
        Dossier dossier =
                sql.sql("SELECT etat, instructeurs FROM habilitation WHERE id = ? FOR UPDATE")
                        .param(id)
                        .query((ligne, i) ->
                                new Dossier(
                                        Etat.valueOf(ligne.getString("etat")),
                                        Sql.liste(ligne, "instructeurs")))
                        .optional()
                        .orElseThrow(ErreurMetier::introuvable);
        if (dossier.instructeurs().contains(acteur.id())) {
            throw Erreurs.separationDesRoles();
        }
        if (!depuis.contains(dossier.etat())) {
            throw ErreurMetier.conflit(sinon);
        }
        sql.sql(
                        "UPDATE habilitation SET etat = ?, motif = ?, decidee_par = ?,"
                                + " decidee_le = ? WHERE id = ?")
                .params(nouvel.name(), motif, acteur.nom(), Sql.date(horloge.instant()), id)
                .update();
    }

    private void verifier(SaisieHabilitation saisie) {
        boolean demarcheDuService =
                sql.sql(
                                "SELECT EXISTS (SELECT FROM demarche WHERE code = ? AND service = ?"
                                        + " AND actif)")
                        .params(saisie.demarche(), saisie.service())
                        .query(Boolean.class)
                        .single();
        if (!demarcheDuService) {
            throw new SaisieRefusee(
                    "demarche", "Cette démarche n'existe pas, ou n'est pas celle de ce service.");
        }
        Set<String> jeux = Set.copyOf(saisie.jeux());
        long connus =
                sql.sql("SELECT count(*) FROM jeu WHERE code = ANY (?) AND actif")
                        .param(Sql.tableau(jeux))
                        .query(Long.class)
                        .single();
        if (connus != jeux.size()) {
            throw new SaisieRefusee("jeux", "Un des jeux n'existe pas, ou n'est plus proposé.");
        }
    }

    private void enregistrerJeux(UUID id, List<String> jeux) {
        sql.sql(
                        "INSERT INTO habilitation_jeu (habilitation, jeu)"
                                + " SELECT ?, jeu FROM unnest(?::text[]) AS jeu"
                                + " ON CONFLICT DO NOTHING")
                .params(id, Sql.tableau(jeux))
                .update();
    }

    private ErreurMetier refus(UUID id, String detail) {
        lire(id);
        return ErreurMetier.conflit(detail);
    }

    private Habilitation tracer(UUID id, String action, Acteur acteur) {
        journal.ecrire(Trace.administration(acteur, "habilitation:" + id, action));
        return lire(id);
    }

    private static Habilitation lire(ResultSet ligne) throws SQLException {
        return new Habilitation(
                ligne.getObject("id", UUID.class),
                ligne.getString("service"),
                ligne.getString("demarche"),
                Sql.liste(ligne, "jeux"),
                Etat.valueOf(ligne.getString("etat")),
                ligne.getString("justification"),
                ligne.getString("motif"),
                ligne.getString("instruite_par"),
                ligne.getString("decidee_par"),
                Sql.instant(ligne, "decidee_le"),
                Sql.instant(ligne, "creee_le"));
    }

    private record Dossier(Etat etat, List<String> instructeurs) {}
}
