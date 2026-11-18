package bf.sira.passerelle.collecte;

import bf.sira.commun.donnees.Sql;
import bf.sira.consentement.ReglagesAccord;
import bf.sira.journal.Journal;
import bf.sira.journal.Resultat;
import bf.sira.journal.Trace;
import bf.sira.journal.TypeLigne;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Efface ce qui a fait son temps : les demandes restées sans réponse, les attestations dont la
 * validité est passée, les essais de code anciens. La passerelle ne garde rien au-delà du
 * nécessaire.
 */
@Component
public class Purge {

    private final JdbcClient sql;
    private final Journal journal;
    private final ReglagesAccord reglageAccord;
    private final Clock horloge;

    Purge(JdbcClient sql, Journal journal, ReglagesAccord reglageAccord, Clock horloge) {
        this.sql = sql;
        this.journal = journal;
        this.reglageAccord = reglageAccord;
        this.horloge = horloge;
    }

    @Scheduled(
            initialDelayString = "${sira.passerelle.purge.intervalle:30s}",
            fixedDelayString = "${sira.passerelle.purge.intervalle:30s}")
    @Transactional
    public void purger() {
        Instant maintenant = horloge.instant();
        expirerLesDemandes(maintenant);
        sql.sql(
                        "UPDATE element SET etat = 'EXPIRE', attestation = NULL WHERE attestation IN"
                                + " (SELECT id FROM attestation WHERE expire_le <= ?)")
                .param(Sql.date(maintenant))
                .update();
        sql.sql("DELETE FROM attestation WHERE expire_le <= ?")
                .param(Sql.date(maintenant))
                .update();
        sql.sql("DELETE FROM essai_rattachement WHERE quand < ?")
                .param(Sql.date(maintenant.minus(reglageAccord.dureeAccord())))
                .update();
    }

    /** Une expiration est journalisée : le citoyen voit qu'une demande est restée sans suite. */
    private void expirerLesDemandes(Instant maintenant) {
        sql.sql(
                        "UPDATE demande d SET etat = 'EXPIREE', code_court_empreinte = NULL,"
                                + " code_verification_empreinte = NULL, designation = NULL FROM"
                                + " demarche m WHERE m.code = d.demarche AND d.etat ="
                                + " 'EN_ATTENTE_ACCORD' AND d.expire_le <= ? RETURNING d.id,"
                                + " d.citoyen, d.service, d.demarche, m.finalite, d.canal")
                .param(Sql.date(maintenant))
                .query(
                        (RowCallbackHandler)
                                ligne ->
                                        journal.ecrire(
                                                Trace.de(TypeLigne.DEMANDE, Resultat.ECHEC)
                                                        .citoyen(ligne.getString("citoyen"))
                                                        .service(ligne.getString("service"))
                                                        .demarche(
                                                                ligne.getString("demarche"),
                                                                ligne.getString("finalite"))
                                                        .canal(ligne.getString("canal"))
                                                        .demande(ligne.getObject("id", UUID.class))
                                                        .detail("EXPIRATION")));
    }
}
