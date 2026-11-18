package bf.sira.commun.erreurs;

import org.springframework.http.HttpStatus;

/**
 * Un refus métier, rendu tel quel à l'appelant au format RFC 9457. Le titre et le détail sont lus
 * par l'appelant : ils ne contiennent ni donnée personnelle ni information technique.
 */
public class ErreurMetier extends RuntimeException {

    private final HttpStatus statut;
    private final String code;
    private final String detail;

    /**
     * @param code identifiant stable de l'erreur, en minuscules avec tirets (ex. {@code
     *     habilitation-absente}) ; il forme le champ {@code type} : {@code urn:sira:erreur:<code>}
     * @param titre libellé court, identique pour toutes les occurrences de ce code
     */
    public ErreurMetier(HttpStatus statut, String code, String titre) {
        this(statut, code, titre, null);
    }

    public ErreurMetier(HttpStatus statut, String code, String titre, String detail) {
        super(titre);
        this.statut = statut;
        this.code = code;
        this.detail = detail;
    }

    /** Objet absent, ou appartenant à un autre : la réponse est la même (contrat §5). */
    public static ErreurMetier introuvable() {
        return new ErreurMetier(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable");
    }

    /** L'action ne convient pas à l'état actuel de l'objet. */
    public static ErreurMetier conflit(String detail) {
        return new ErreurMetier(
                HttpStatus.CONFLICT, "conflit", "Conflit avec l'état actuel", detail);
    }

    public HttpStatus statut() {
        return statut;
    }

    public String code() {
        return code;
    }

    public String titre() {
        return getMessage();
    }

    public String detail() {
        return detail;
    }
}
