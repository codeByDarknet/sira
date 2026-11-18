package bf.sira.consentement;

import bf.sira.consentement.Vues.JeuVu;
import bf.sira.consentement.Vues.ServiceVu;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Une demande telle que la lit le citoyen sur l'écran d'accord (API, « Espace citoyen »).
 *
 * @param codeVerificationExige l'accord demande de taper le code de vérification que montre l'écran
 *     du service (canal CODE, et NOTIFICATION par identifiant unique) ; ce code n'est jamais rendu
 *     au citoyen
 */
public record DemandeCitoyen(
        UUID id,
        String etat,
        String canal,
        Instant creeeLe,
        Instant expireLe,
        boolean codeVerificationExige,
        ServiceVu service,
        DemarcheVue demarche,
        List<JeuVu> jeux) {

    /**
     * @param dureeAutorisationJours durée de l'accord demandé ; {@code 0} = pour cette fois
     */
    public record DemarcheVue(
            String code, String libelle, String finalite, int dureeAutorisationJours) {}

    /**
     * @param retourUrl canal REDIRECTION : où le navigateur doit revenir, code de retour compris
     */
    public record Decision(String etat, String retourUrl) {}
}
