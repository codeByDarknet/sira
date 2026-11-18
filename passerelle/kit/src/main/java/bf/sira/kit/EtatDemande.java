package bf.sira.kit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * État d'une demande et de ses éléments.
 *
 * @param pseudonyme pseudonyme du citoyen chez ce service, connu une fois l'accord donné
 */
public record EtatDemande(
        UUID id, EtatDemandeCode etat, String pseudonyme, List<Element> elements) {

    public EtatDemande {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    /**
     * @param jeu code du jeu de données
     * @param attestation l'attestation chiffrée, à ouvrir avec {@link
     *     ClientSira#ouvrir(EtatDemande, String, Jeu)}
     * @param prochaineTentativeLe prochain essai auprès du registre, pour un élément en attente
     */
    public record Element(
            String jeu,
            EtatElement etat,
            String attestation,
            @JsonProperty("prochaine_tentative_le") Instant prochaineTentativeLe) {}
}
