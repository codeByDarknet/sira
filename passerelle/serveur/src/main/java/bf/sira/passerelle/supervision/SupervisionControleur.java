package bf.sira.passerelle.supervision;

import bf.sira.commun.donnees.Sql;
import bf.sira.resilience.Disjoncteurs;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ce que la console montre de la marche du socle : l'état des registres et de la file, et les
 * chiffres du jour. Rien ici ne désigne un citoyen.
 */
@RestController
@RequestMapping("/v1/admin")
class SupervisionControleur {

    private final JdbcClient sql;
    private final Disjoncteurs disjoncteurs;
    private final Clock horloge;

    SupervisionControleur(JdbcClient sql, Disjoncteurs disjoncteurs, Clock horloge) {
        this.sql = sql;
        this.disjoncteurs = disjoncteurs;
        this.horloge = horloge;
    }

    /**
     * @param disjoncteur état vu par cette instance de la passerelle
     * @param enFile éléments qui attendent ce registre
     */
    record EtatRegistre(
            String code,
            String nom,
            Disjoncteurs.Etat disjoncteur,
            long enFile,
            Instant dernierSuccesLe,
            Instant dernierEchecLe) {}

    record EtatFile(long aTraiter, Instant plusAncienLe) {}

    record Etat(List<EtatRegistre> registres, EtatFile file) {}

    /**
     * @param piecesEviteesTotal réponses remises à un service depuis l'origine : autant de pièces
     *     que le citoyen n'a pas eu à fournir
     */
    record Statistiques(
            long demandesJour,
            long accordsJour,
            long refusJour,
            long consultationsJour,
            long piecesEviteesTotal) {}

    @GetMapping("/etat")
    Etat etat() {
        List<EtatRegistre> registres =
                sql.sql(
                                "SELECT r.code, r.nom, r.dernier_succes_le, r.dernier_echec_le,"
                                        + " (SELECT count(*) FROM element e WHERE e.registre = r.code"
                                        + " AND e.etat = 'A_TRAITER') AS en_file FROM registre r WHERE"
                                        + " r.actif ORDER BY r.code")
                        .query((ligne, i) ->
                                new EtatRegistre(
                                        ligne.getString("code"),
                                        ligne.getString("nom"),
                                        disjoncteurs.etat(ligne.getString("code")),
                                        ligne.getLong("en_file"),
                                        Sql.instant(ligne, "dernier_succes_le"),
                                        Sql.instant(ligne, "dernier_echec_le")))
                        .list();
        EtatFile file =
                sql.sql(
                                "SELECT count(*) AS a_traiter, min(cree_le) AS plus_ancien_le FROM"
                                        + " element WHERE etat = 'A_TRAITER'")
                        .query((ligne, i) ->
                                new EtatFile(
                                        ligne.getLong("a_traiter"),
                                        Sql.instant(ligne, "plus_ancien_le")))
                        .single();
        return new Etat(registres, file);
    }

    @GetMapping("/statistiques")
    Statistiques statistiques() {
        return sql.sql(
                        "SELECT (SELECT count(*) FROM demande WHERE creee_le >= :jour), count(*)"
                                + " FILTER (WHERE type = 'ACCORD' AND horodatage >= :jour), count(*)"
                                + " FILTER (WHERE type = 'REFUS' AND horodatage >= :jour), count(*)"
                                + " FILTER (WHERE type = 'CONSULTATION' AND resultat = 'REUSSI' AND"
                                + " horodatage >= :jour), count(*) FILTER (WHERE type IN"
                                + " ('CONSULTATION', 'REUTILISATION') AND resultat = 'REUSSI') FROM"
                                + " journal")
                .param("jour", Sql.date(horloge.instant().truncatedTo(ChronoUnit.DAYS)))
                .query((ligne, i) ->
                        new Statistiques(
                                ligne.getLong(1),
                                ligne.getLong(2),
                                ligne.getLong(3),
                                ligne.getLong(4),
                                ligne.getLong(5)))
                .single();
    }
}
