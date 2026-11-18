package bf.sira.journal;

import bf.sira.commun.donnees.Sql;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écrit le journal (contrat §10). Chaque ligne s'ajoute dans la transaction de l'action qu'elle
 * constate : l'action et sa trace réussissent ou échouent ensemble.
 */
@Component
public class Journal {

    /** Verrou consultatif qui met les écritures en file : la chaîne n'a qu'une seule extrémité. */
    private static final long VERROU = 7_472_101L;

    private final JdbcClient sql;
    private final CitoyensJournal citoyens;
    private final Clock horloge;

    Journal(JdbcClient sql, CitoyensJournal citoyens, Clock horloge) {
        this.sql = sql;
        this.citoyens = citoyens;
        this.horloge = horloge;
    }

    /**
     * @return l'identifiant de la ligne écrite
     */
    @Transactional
    public UUID ecrire(Trace trace) {
        sql.sql("SELECT pg_advisory_xact_lock(?)")
                .param(VERROU)
                .query((RowCallbackHandler) ligne -> {});
        Extremite extremite =
                sql.sql("SELECT numero, hachage FROM journal ORDER BY numero DESC LIMIT 1")
                        .query((ligne, i) -> new Extremite(ligne.getLong(1), ligne.getBytes(2)))
                        .optional()
                        .orElse(new Extremite(0, Chaine.ORIGINE));

        long numero = extremite.numero() + 1;
        UUID id = UUID.randomUUID();
        Instant horodatage = Chaine.arrondir(horloge.instant());
        String citoyen = trace.citoyen == null ? null : citoyens.pseudonymeJournal(trace.citoyen);
        byte[] hachage =
                Chaine.hachage(
                        extremite.hachage(),
                        numero,
                        id,
                        horodatage,
                        trace.type,
                        trace.resultat,
                        citoyen,
                        trace.service,
                        trace.registre,
                        trace.jeu,
                        trace.niveau,
                        trace.demarche,
                        trace.finalite,
                        trace.canal,
                        trace.idEchange,
                        trace.demande,
                        trace.autorisation,
                        trace.detail,
                        trace.acteur,
                        trace.objet);
        sql.sql(
                        "INSERT INTO journal ("
                                + Chaine.COLONNES
                                + ", hachage_precedent, hachage) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
                                + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        numero,
                        id,
                        Sql.date(horodatage),
                        trace.type.name(),
                        trace.resultat.name(),
                        citoyen,
                        trace.service,
                        trace.registre,
                        trace.jeu,
                        trace.niveau,
                        trace.demarche,
                        trace.finalite,
                        trace.canal,
                        trace.idEchange,
                        trace.demande,
                        trace.autorisation,
                        trace.detail,
                        trace.acteur,
                        trace.objet,
                        extremite.hachage(),
                        hachage)
                .update();
        return id;
    }

    private record Extremite(long numero, byte[] hachage) {}
}
