package bf.sira.passerelle.demandes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le service reçoit à la création (contrat §7.1).
 *
 * @param urlAccord où envoyer le citoyen (canal REDIRECTION) ou contenu du QR code (canal CODE)
 * @param codeCourt canal CODE : ce que le citoyen tape dans son espace
 * @param codeVerification canal CODE, et NOTIFICATION par identifiant unique : à afficher en
 *     évidence sur l'écran du service, le citoyen le tape dans son espace pour accepter. Les deux
 *     codes ne sont rendus qu'à la création : une requête rejouée les rend nuls.
 */
public record DemandeCreee(
        UUID id,
        String etat,
        String urlAccord,
        String codeCourt,
        String codeVerification,
        Instant expireLe,
        List<JeuDemande> jeux) {}
