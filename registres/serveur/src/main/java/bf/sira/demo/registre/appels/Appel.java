package bf.sira.demo.registre.appels;

import bf.sira.demo.registre.pannes.Panne;
import java.time.Instant;
import java.util.UUID;

/**
 * Un appel reçu par l'API interne. Il ne dit pas quelle personne était concernée.
 *
 * @param statut statut HTTP rendu, ou {@code null} si le registre n'a pas répondu
 * @param origine adresse de la machine qui a appelé
 */
public record Appel(
        UUID id,
        Instant horodatage,
        Type type,
        Panne.Mode mode,
        Integer statut,
        int dureeMs,
        String origine) {

    public enum Type {
        FICHE,
        DOCUMENT
    }
}
