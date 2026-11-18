package bf.sira.resilience;

import bf.sira.commun.donnees.Sql;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La file d'attente durable (contrat §6) : les éléments restés {@code A_TRAITER} sont redemandés
 * jusqu'à ce que leur registre réponde. La file est la table {@code element} elle-même : rien ne se
 * perd à l'arrêt, et plusieurs instances se partagent le travail sans se gêner ({@code FOR UPDATE
 * SKIP LOCKED}, puis un bail qui retombe si l'instance s'arrête).
 */
@Component
class FileAttente {

    private final JdbcClient sql;
    private final Reprise reprise;
    private final Disjoncteurs disjoncteurs;
    private final ReglageFile reglage;
    private final Clock horloge;

    FileAttente(
            JdbcClient sql,
            Reprise reprise,
            Disjoncteurs disjoncteurs,
            ReglageFile reglage,
            Clock horloge) {
        this.sql = sql;
        this.reprise = reprise;
        this.disjoncteurs = disjoncteurs;
        this.reglage = reglage;
        this.horloge = horloge;
    }

    @Scheduled(
            initialDelayString = "${sira.passerelle.file.intervalle:1s}",
            fixedDelayString = "${sira.passerelle.file.intervalle:1s}")
    void relever() {
        Instant maintenant = horloge.instant();
        // Un registre dont le disjoncteur est ouvert ne consomme ni appel ni tentative.
        List<UUID> reserves =
                sql.sql(
                                "UPDATE element SET bail_jusqu_a = ? WHERE id IN (SELECT id FROM"
                                        + " element WHERE etat = 'A_TRAITER' AND"
                                        + " prochaine_tentative_le <= ? AND (bail_jusqu_a IS NULL OR"
                                        + " bail_jusqu_a < ?) AND NOT (registre = ANY (?)) ORDER BY"
                                        + " prochaine_tentative_le LIMIT ? FOR UPDATE SKIP LOCKED)"
                                        + " RETURNING id")
                        .params(
                                Sql.date(maintenant.plus(reglage.bail())),
                                Sql.date(maintenant),
                                Sql.date(maintenant),
                                Sql.tableau(disjoncteurs.ouverts()),
                                reglage.lot())
                        .query(UUID.class)
                        .list();
        if (reserves.isEmpty()) {
            return;
        }
        reprise.reprendre(reserves, reglage.bail());
    }
}
