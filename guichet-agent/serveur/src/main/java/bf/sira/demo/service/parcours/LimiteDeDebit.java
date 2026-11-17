package bf.sira.demo.service.parcours;

import bf.sira.demo.service.configuration.ProprietesService;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Borne le nombre de demandes ouvertes par minute, par navigateur et pour toute la plateforme :
 * chaque demande sollicite Sira, et un visiteur anonyme ne doit pas pouvoir l'inonder. Le plafond
 * global tient même si le visiteur change de session à chaque appel.
 */
@Component
class LimiteDeDebit {

    private final ProprietesService service;
    private final Clock horloge;
    private final Map<String, Integer> compteurs = new HashMap<>();
    private long minute;
    private int total;

    LimiteDeDebit(ProprietesService service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    /** Vrai si ce navigateur peut encore ouvrir une demande dans la minute en cours. */
    synchronized boolean admettre(String navigateur) {
        long courante = horloge.millis() / 60_000;
        if (courante != minute) {
            minute = courante;
            total = 0;
            compteurs.clear();
        }
        if (total >= service.demandesParMinuteTotal()
                || compteurs.getOrDefault(navigateur, 0) >= service.demandesParMinute()) {
            return false;
        }
        total++;
        compteurs.merge(navigateur, 1, Integer::sum);
        return true;
    }
}
