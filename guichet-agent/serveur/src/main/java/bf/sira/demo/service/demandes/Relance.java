package bf.sira.demo.service.demandes;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quand un registre est en panne, le citoyen n'a pas à revenir : le service relit lui-même, à
 * intervalle régulier, les demandes encore incomplètes, et le dossier se complète tout seul.
 */
@Component
public class Relance {

    private static final Logger journal = LoggerFactory.getLogger(Relance.class);
    private static final int LOT = 100;
    private static final Duration MARGE_EXPIRATION = Duration.ofMinutes(1);
    private static final Duration CONSERVATION_SANS_DOSSIER = Duration.ofDays(1);

    private final DepotDemandes demandes;
    private final SuiviDemandes suivi;
    private final Clock horloge;

    Relance(DepotDemandes demandes, SuiviDemandes suivi, Clock horloge) {
        this.demandes = demandes;
        this.suivi = suivi;
        this.horloge = horloge;
    }

    @Scheduled(
            initialDelayString = "${sira.service.relance:10s}",
            fixedDelayString = "${sira.service.relance:10s}")
    public void relancer() {
        demandes.expirer(horloge.instant().minus(MARGE_EXPIRATION));
        demandes.purger(horloge.instant().minus(CONSERVATION_SANS_DOSSIER));
        for (DemandeSuivie demande : demandes.aRelancer(LOT)) {
            try {
                suivi.synchroniser(demande);
            } catch (RuntimeException echec) {
                // Une demande en échec ne doit pas retenir les autres ; elle sera relue au tour
                // suivant.
                journal.warn(
                        "Relance en échec : demande={} type={}",
                        demande.id(),
                        echec.getClass().getSimpleName());
            }
        }
    }
}
