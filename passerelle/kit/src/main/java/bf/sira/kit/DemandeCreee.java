package bf.sira.kit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse à l'ouverture d'une demande.
 *
 * @param urlAccord adresse de l'écran d'accord, à suivre ou à présenter en QR code
 * @param codeCourt code que le citoyen peut saisir dans son espace
 * @param codeVerification code à 6 chiffres à afficher, que le citoyen compare à celui de l'écran
 *     d'accord
 */
public record DemandeCreee(
        UUID id,
        EtatDemandeCode etat,
        @JsonProperty("url_accord") String urlAccord,
        @JsonProperty("code_court") String codeCourt,
        @JsonProperty("code_verification") String codeVerification,
        @JsonProperty("expire_le") Instant expireLe,
        List<Jeu> jeux) {

    public DemandeCreee {
        jeux = jeux == null ? List.of() : List.copyOf(jeux);
    }
}
