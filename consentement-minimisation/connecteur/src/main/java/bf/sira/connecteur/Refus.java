package bf.sira.connecteur;

import bf.sira.commun.erreurs.ErreurMetier;
import org.springframework.http.HttpStatus;

/**
 * Le connecteur ne répond pas à la requête. Le motif est écrit au journal local ; ni le motif ni le
 * détail rendu à la passerelle ne contiennent une valeur du registre.
 */
public final class Refus extends ErreurMetier {

    private static final String JEU_REFUSE = "jeu-refuse";
    private static final String TITRE_JEU_REFUSE = "Jeu refusé par le registre";

    public enum Motif {
        PERSONNE_INCONNUE(
                HttpStatus.NOT_FOUND, "personne-inconnue", "Personne inconnue du registre", null),
        CHAMP_NON_EXPOSE("Le registre n'expose pas ce champ."),
        NIVEAU_DEPASSE("Le registre n'expose pas ce champ à ce niveau."),
        QUESTION_NON_AUTORISEE("Le registre n'autorise pas cette question sur ce champ."),
        DOCUMENT_NON_AUTORISE("Le registre ne délivre pas ce document."),
        JEU_INVALIDE("La définition du jeu est incomplète ou mal formée."),
        REGLE_INAPPLICABLE("La règle ne s'applique pas aux données du registre."),
        REGISTRE_INDISPONIBLE(
                HttpStatus.SERVICE_UNAVAILABLE,
                "registre-indisponible",
                "Registre momentanément indisponible",
                null);

        private final HttpStatus statut;
        private final String code;
        private final String titre;
        private final String detail;

        Motif(String detail) {
            this(HttpStatus.UNPROCESSABLE_CONTENT, JEU_REFUSE, TITRE_JEU_REFUSE, detail);
        }

        Motif(HttpStatus statut, String code, String titre, String detail) {
            this.statut = statut;
            this.code = code;
            this.titre = titre;
            this.detail = detail;
        }
    }

    private final Motif motif;

    public Refus(Motif motif) {
        super(motif.statut, motif.code, motif.titre, motif.detail);
        this.motif = motif;
    }

    public Motif motif() {
        return motif;
    }
}
