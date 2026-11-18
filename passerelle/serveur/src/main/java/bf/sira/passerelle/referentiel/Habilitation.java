package bf.sira.passerelle.referentiel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La décision qui autorise un service à demander une liste de jeux pour une démarche.
 *
 * @param motif motif du rejet ou de la suspension
 * @param instruitePar nom de la personne qui a préparé le dossier
 * @param decideePar nom de la personne qui l'a approuvé, rejeté ou suspendu
 */
public record Habilitation(
        UUID id,
        String service,
        String demarche,
        List<String> jeux,
        Etat etat,
        String justification,
        String motif,
        String instruitePar,
        String decideePar,
        Instant decideeLe,
        Instant creeeLe) {

    public enum Etat {
        BROUILLON,
        SOUMISE,
        APPROUVEE,
        REJETEE,
        SUSPENDUE
    }
}
