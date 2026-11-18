package bf.sira.passerelle.demandes;

import bf.sira.passerelle.collecte.Collecte;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Interroge les registres d'une demande que le citoyen vient d'accepter, sans le faire attendre.
 *
 * <p>Attendre la collecte avant de répondre ajoutait jusqu'au budget de l'essai immédiat au clic «
 * Autoriser » : l'espace citoyen, qui n'attend la passerelle que quelques secondes, abandonnait
 * alors et annonçait une erreur alors que l'accord était déjà enregistré. Les éléments à obtenir
 * sont inscrits dans la transaction de l'accord : si cet essai est interrompu (arrêt de la
 * passerelle), la file d'attente les reprend. Le service suit la collecte par sa propre route.
 */
@Component
public class CollectesApresAccord {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CollectesApresAccord.class);

    private final Collecte collecte;
    private final AtomicInteger enCours = new AtomicInteger();

    CollectesApresAccord(Collecte collecte) {
        this.collecte = collecte;
    }

    void lancer(UUID demande) {
        enCours.incrementAndGet();
        try {
            Thread.ofVirtual()
                    .name("collecte-accord")
                    .start(() -> {
                        try {
                            collecte.essayerMaintenant(demande);
                        } catch (RuntimeException e) {
                            JOURNAL.warn(
                                    "Collecte après accord interrompue : demande={}", demande, e);
                        } finally {
                            enCours.decrementAndGet();
                        }
                    });
        } catch (RuntimeException e) {
            enCours.decrementAndGet();
            throw e;
        }
    }

    /** Vrai quand aucune collecte lancée après un accord n'est en cours. */
    public boolean auRepos() {
        return enCours.get() == 0;
    }
}
