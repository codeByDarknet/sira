package bf.sira.consentement;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.consentement.AutorisationCitoyen.DemarcheVue;
import bf.sira.journal.Journal;
import bf.sira.journal.Resultat;
import bf.sira.journal.Trace;
import bf.sira.journal.TypeLigne;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les autorisations du citoyen : ce qu'il a ouvert, à qui, et le retrait (contrat §6). Retirer a un
 * effet immédiat : plus rien n'est demandé aux registres, et ce qui attendait en dépôt est effacé.
 */
@Component
public class Autorisations {

    private static final String ETAT =
            "CASE WHEN a.retiree_le IS NOT NULL THEN 'RETIREE' WHEN a.expire_le <= :maintenant THEN"
                    + " 'EXPIREE' ELSE 'ACTIVE' END";
    private static final String COLONNES =
            "a.id, "
                    + ETAT
                    + " AS etat, a.accordee_le, a.expire_le, a.retiree_le, a.jeux, a.service, s.nom"
                    + " AS service_nom, s.organisme, a.demarche, m.libelle, m.finalite, (SELECT"
                    + " count(*) FROM journal j WHERE j.autorisation = a.id AND j.resultat ="
                    + " 'REUSSI' AND j.type IN ('CONSULTATION', 'REUTILISATION')) AS consultations,"
                    + " (SELECT max(j.horodatage) FROM journal j WHERE j.autorisation = a.id AND"
                    + " j.resultat = 'REUSSI' AND j.type IN ('CONSULTATION', 'REUTILISATION')) AS"
                    + " derniere_consultation_le";
    private static final String SOURCE =
            "autorisation a JOIN membre s ON s.code = a.service JOIN demarche m ON m.code ="
                    + " a.demarche WHERE a.citoyen = :citoyen";

    private final JdbcClient sql;
    private final Pages pages;
    private final Vues vues;
    private final Journal journal;
    private final Echanges echanges;
    private final Clock horloge;

    Autorisations(
            JdbcClient sql,
            Pages pages,
            Vues vues,
            Journal journal,
            Echanges echanges,
            Clock horloge) {
        this.sql = sql;
        this.pages = pages;
        this.vues = vues;
        this.journal = journal;
        this.echanges = echanges;
        this.horloge = horloge;
    }

    public Page<AutorisationCitoyen> lister(String citoyen, String etat, Pagination pagination) {
        return pages.lire(
                COLONNES,
                SOURCE + " AND (CAST(:etat AS text) IS NULL OR " + ETAT + " = :etat)",
                "a.accordee_le DESC, a.id",
                Sql.parametres(
                        "citoyen",
                        citoyen,
                        "etat",
                        Sql.filtre(etat),
                        "maintenant",
                        Sql.date(horloge.instant())),
                pagination,
                (ligne, i) -> lire(ligne));
    }

    public AutorisationCitoyen lire(String citoyen, UUID id) {
        return sql.sql("SELECT " + COLONNES + " FROM " + SOURCE + " AND a.id = :id")
                .param("citoyen", citoyen)
                .param("maintenant", Sql.date(horloge.instant()))
                .param("id", id)
                .query((ligne, i) -> lire(ligne))
                .optional()
                .orElseThrow(ErreurMetier::introuvable);
    }

    /** Retire l'accès. Retirer deux fois ne change rien. */
    @Transactional
    public void retirer(String citoyen, UUID id) {
        Instant maintenant = horloge.instant();
        Acces acces =
                sql.sql(
                                "SELECT a.service, a.demarche, m.finalite, a.retiree_le IS NOT NULL AS"
                                        + " retiree FROM autorisation a JOIN demarche m ON m.code ="
                                        + " a.demarche WHERE a.id = ? AND a.citoyen = ?")
                        .params(id, citoyen)
                        .query((ligne, i) ->
                                new Acces(
                                        ligne.getString("service"),
                                        ligne.getString("demarche"),
                                        ligne.getString("finalite"),
                                        ligne.getBoolean("retiree")))
                        .optional()
                        .orElseThrow(ErreurMetier::introuvable);
        if (acces.retiree()) {
            return;
        }
        // Même ordre de verrouillage que la collecte : les demandes d'abord.
        List<UUID> demandes =
                sql.sql(
                                "SELECT id FROM demande WHERE citoyen = ? AND service = ? AND demarche"
                                        + " = ? ORDER BY id FOR UPDATE")
                        .params(citoyen, acces.service(), acces.demarche())
                        .query(UUID.class)
                        .list();
        sql.sql("UPDATE autorisation SET retiree_le = ? WHERE id = ?")
                .params(Sql.date(maintenant), id)
                .update();
        // Plus rien n'est demandé ni remis : éléments en attente abandonnés, dépôt vidé.
        sql.sql(
                        "UPDATE element SET etat = 'EXPIRE', attestation = NULL, bail_jusqu_a = NULL"
                                + " WHERE demande = ANY (?) AND (etat = 'A_TRAITER' OR attestation IS"
                                + " NOT NULL)")
                .param(demandes.toArray(UUID[]::new))
                .update();
        sql.sql("DELETE FROM attestation WHERE citoyen = ? AND service = ? AND demarche = ?")
                .params(citoyen, acces.service(), acces.demarche())
                .update();
        demandes.forEach(echanges::actualiser);
        journal.ecrire(
                Trace.de(TypeLigne.RETRAIT, Resultat.REUSSI)
                        .citoyen(citoyen)
                        .service(acces.service())
                        .demarche(acces.demarche(), acces.finalite())
                        .autorisation(id));
    }

    private AutorisationCitoyen lire(ResultSet ligne) throws SQLException {
        return new AutorisationCitoyen(
                ligne.getObject("id", UUID.class),
                ligne.getString("etat"),
                Sql.instant(ligne, "accordee_le"),
                Sql.instant(ligne, "expire_le"),
                Sql.instant(ligne, "retiree_le"),
                Vues.service(ligne),
                new DemarcheVue(
                        ligne.getString("demarche"),
                        ligne.getString("libelle"),
                        ligne.getString("finalite")),
                vues.jeux(Sql.liste(ligne, "jeux")),
                ligne.getLong("consultations"),
                Sql.instant(ligne, "derniere_consultation_le"));
    }

    private record Acces(String service, String demarche, String finalite, boolean retiree) {}
}
