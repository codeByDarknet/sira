package bf.sira.journal;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.jetons.Acteur;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Un citoyen conteste un accès lu dans son historique ; l'administration lui répond. */
@Component
public class Signalements {

    private final JdbcClient sql;
    private final Pages pages;
    private final CitoyensJournal citoyens;
    private final Journal journal;
    private final Clock horloge;

    Signalements(
            JdbcClient sql, Pages pages, CitoyensJournal citoyens, Journal journal, Clock horloge) {
        this.sql = sql;
        this.pages = pages;
        this.citoyens = citoyens;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * @param ligne la ligne contestée, telle que la lit l'administration
     */
    public record Signalement(
            UUID id,
            Ligne ligne,
            String motif,
            Instant creeLe,
            String reponse,
            String traitePar,
            Instant traiteLe) {}

    public record Recu(UUID id, Instant creeLe) {}

    /**
     * @throws ErreurMetier 404 si la ligne n'est pas une ligne de ce citoyen
     */
    @Transactional
    public Recu signaler(String citoyen, UUID ligne, String motif) {
        String service =
                sql.sql("SELECT service FROM journal WHERE id = ? AND citoyen = ?")
                        .params(ligne, citoyens.pseudonymeJournal(citoyen))
                        .query((resultat, i) -> resultat.getString(1))
                        .optional()
                        .orElseThrow(ErreurMetier::introuvable);
        Recu recu = new Recu(UUID.randomUUID(), horloge.instant());
        int cree =
                sql.sql(
                                "INSERT INTO signalement (id, ligne, motif, cree_le)"
                                        + " VALUES (?, ?, ?, ?) ON CONFLICT (ligne) DO NOTHING")
                        .params(recu.id(), ligne, motif, Sql.date(recu.creeLe()))
                        .update();
        if (cree == 0) {
            throw ErreurMetier.conflit("Vous avez déjà signalé cet accès.");
        }
        journal.ecrire(
                Trace.de(TypeLigne.SIGNALEMENT, Resultat.REUSSI)
                        .citoyen(citoyen)
                        .service(service)
                        .objet("ligne:" + ligne));
        return recu;
    }

    /**
     * @param etat {@code A_TRAITER}, {@code TRAITE}, ou rien pour tout voir
     */
    public Page<Signalement> lister(String etat, Pagination pagination) {
        return pages.lire(
                Ligne.COLONNES
                        + ", sg.id AS signalement, sg.motif, sg.cree_le, sg.reponse,"
                        + " sg.traite_par, sg.traite_le",
                Ligne.SOURCE
                        + " JOIN signalement sg ON sg.ligne = j.id"
                        + " WHERE (CAST(:etat AS text) IS NULL"
                        + " OR (:etat = 'TRAITE') = (sg.traite_le IS NOT NULL))",
                "sg.cree_le DESC",
                Sql.parametres("etat", Sql.filtre(etat)),
                pagination,
                (ligne, i) -> lire(ligne));
    }

    @Transactional
    public void traiter(UUID id, String reponse, Acteur acteur) {
        int traites =
                sql.sql(
                                "UPDATE signalement SET reponse = ?, traite_par = ?, traite_le = ?"
                                        + " WHERE id = ? AND traite_le IS NULL")
                        .params(reponse, acteur.nom(), Sql.date(horloge.instant()), id)
                        .update();
        if (traites == 0) {
            boolean existe =
                    sql.sql("SELECT count(*) FROM signalement WHERE id = ?")
                                    .param(id)
                                    .query(Long.class)
                                    .single()
                            > 0;
            throw existe
                    ? ErreurMetier.conflit("Ce signalement a déjà reçu une réponse.")
                    : ErreurMetier.introuvable();
        }
        journal.ecrire(Trace.administration(acteur, "signalement:" + id, "TRAITEMENT"));
    }

    private static Signalement lire(ResultSet ligne) throws SQLException {
        return new Signalement(
                ligne.getObject("signalement", UUID.class),
                Ligne.lire(ligne, true),
                ligne.getString("motif"),
                Sql.instant(ligne, "cree_le"),
                ligne.getString("reponse"),
                ligne.getString("traite_par"),
                Sql.instant(ligne, "traite_le"));
    }
}
