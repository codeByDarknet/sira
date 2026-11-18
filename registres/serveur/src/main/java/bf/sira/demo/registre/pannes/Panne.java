package bf.sira.demo.registre.pannes;

import java.time.Instant;

/**
 * L'état du panneau de pannes, appliqué à l'API interne.
 *
 * @param delaiMs attente ajoutée à chaque réponse en mode {@code LENT}
 */
public record Panne(Mode mode, int delaiMs, Instant modifieeLe) {

    public enum Mode {
        /** Le registre répond. */
        NORMAL,
        /** Le registre répond après un délai. */
        LENT,
        /** Le registre répond par une erreur 500. */
        ERREURS,
        /** Le registre accepte la connexion et ne répond pas. */
        MUET
    }
}
