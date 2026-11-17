package bf.sira.consentement;

import bf.sira.commun.erreurs.ErreurMetier;
import org.springframework.http.HttpStatus;

/** Les refus métier des demandes et de l'accord, tels que les lisent les services et les écrans. */
public final class Erreurs {

    private Erreurs() {}

    public static ErreurMetier habilitationAbsente() {
        return new ErreurMetier(
                HttpStatus.FORBIDDEN,
                "habilitation-absente",
                "Habilitation absente",
                "Aucune habilitation approuvée ne couvre cette démarche pour ce service.");
    }

    /** Demande absente, ou appartenant à un autre : la réponse est la même. */
    public static ErreurMetier demandeIntrouvable() {
        return new ErreurMetier(
                HttpStatus.NOT_FOUND,
                "demande-introuvable",
                "Demande introuvable",
                "Cette demande n'existe pas ou n'existe plus.");
    }

    public static ErreurMetier demandeExpiree() {
        return new ErreurMetier(
                HttpStatus.GONE,
                "demande-expiree",
                "Demande expirée",
                "Le délai pour répondre à cette demande est dépassé. Recommencez depuis le"
                        + " service.");
    }

    public static ErreurMetier codeInvalide(HttpStatus statut) {
        return new ErreurMetier(
                statut,
                "code-invalide",
                "Code invalide",
                "Ce code n'est pas reconnu ou n'est plus valable.");
    }

    /** Le code de vérification tapé n'est pas celui que montre l'écran du service. */
    public static ErreurMetier codeVerificationInvalide() {
        return new ErreurMetier(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "code-invalide",
                "Code invalide",
                "Ce n'est pas le code affiché par le service. Vérifiez que vous répondez à la"
                        + " demande que vous avez faite vous-même.");
    }

    public static ErreurMetier decisionDejaPrise() {
        return new ErreurMetier(
                HttpStatus.CONFLICT,
                "decision-deja-prise",
                "Décision déjà prise",
                "Vous avez déjà répondu à cette demande.");
    }

    public static ErreurMetier citoyenInconnu() {
        return new ErreurMetier(
                HttpStatus.NOT_FOUND,
                "citoyen-inconnu",
                "Citoyen inconnu",
                "Ce pseudonyme ne désigne personne pour ce service.");
    }

    public static ErreurMetier tropDEssais() {
        return new ErreurMetier(
                HttpStatus.TOO_MANY_REQUESTS,
                "trop-de-requetes",
                "Trop de requêtes",
                "Trop d'essais en peu de temps. Attendez quelques minutes, puis réessayez.");
    }
}
