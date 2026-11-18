package bf.sira.demo.service.dossiers;

import java.time.Instant;
import java.util.UUID;

/**
 * Le dossier d'un citoyen.
 *
 * @param pseudonyme le citoyen tel que Sira le désigne chez ce service ; ne sort jamais du serveur
 * @param deposeLe date du dépôt ; ce que la personne a déclaré ne change plus ensuite
 */
public record Dossier(
        UUID id,
        String numero,
        String pseudonyme,
        EtatDossier etat,
        Instant creeLe,
        Instant modifieLe,
        Instant deposeLe) {}
