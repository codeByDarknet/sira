package bf.sira.demo.service.demandes;

import bf.sira.kit.Canal;
import bf.sira.kit.EtatDemandeCode;
import bf.sira.kit.Jeu;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Une demande ouverte auprès de Sira, telle que le service la suit.
 *
 * @param id identifiant donné par la passerelle
 * @param codeRetour code reçu au retour du navigateur (canal {@code REDIRECTION}), gardé tant que
 *     la demande n'est pas terminée
 * @param dossierId le dossier concerné, connu une fois l'accord donné
 */
public record DemandeSuivie(
        UUID id,
        Canal canal,
        Origine origine,
        EtatDemandeCode etat,
        List<Jeu> jeux,
        String codeRetour,
        UUID dossierId,
        Instant creeeLe,
        Instant expireLe) {}
