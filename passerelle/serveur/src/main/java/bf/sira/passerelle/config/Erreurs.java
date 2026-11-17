package bf.sira.passerelle.config;

import bf.sira.commun.erreurs.ErreurMetier;
import org.springframework.http.HttpStatus;

/**
 * Les refus métier propres au référentiel de la passerelle. Ceux de l'accord du citoyen sont dans
 * {@link bf.sira.consentement.Erreurs} ; le conflit d'état est {@link ErreurMetier#conflit}.
 */
public final class Erreurs {

    private Erreurs() {}

    public static ErreurMetier separationDesRoles() {
        return new ErreurMetier(
                HttpStatus.FORBIDDEN,
                "separation-des-roles",
                "Séparation des rôles",
                "Vous avez préparé cette habilitation : une autre personne doit la décider.");
    }
}
