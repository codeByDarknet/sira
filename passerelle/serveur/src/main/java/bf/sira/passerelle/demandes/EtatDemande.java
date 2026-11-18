package bf.sira.passerelle.demandes;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le service lit en suivant sa demande (contrat §7.1).
 *
 * @param pseudonyme le citoyen, tel que ce service le connaîtra toujours ; présent après l'accord
 */
public record EtatDemande(UUID id, String etat, String pseudonyme, List<Element> elements) {

    /**
     * @param attestation JWE chiffré pour le service ; la passerelle ne peut pas le lire
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Element(
            String jeu, String etat, String attestation, Instant prochaineTentativeLe) {}
}
