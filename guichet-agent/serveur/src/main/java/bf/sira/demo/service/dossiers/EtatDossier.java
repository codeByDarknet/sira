package bf.sira.demo.service.dossiers;

import bf.sira.kit.EtatElement;
import java.util.List;

/** Où en est un dossier. Aucun état ne vaut rejet : un « non » se décide devant un agent. */
public enum EtatDossier {
    /** Au moins un registre n'a pas encore répondu. */
    EN_VERIFICATION,
    /** Toutes les conditions sont vérifiées. */
    COMPLET,
    /** Une condition n'est pas remplie ou n'a pas pu être vérifiée : un agent tranche. */
    A_EXAMINER;

    static EtatDossier selon(List<Verification> verifications) {
        if (verifications.stream().anyMatch(v -> v.etat() == EtatElement.A_TRAITER)) {
            return EN_VERIFICATION;
        }
        boolean conforme =
                verifications.stream()
                        .allMatch(v ->
                                v.etat().porteUneAttestation()
                                        && !Boolean.FALSE.equals(v.valeur()));
        return conforme ? COMPLET : A_EXAMINER;
    }
}
