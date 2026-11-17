package bf.sira.demo.service.configuration;

import bf.sira.commun.erreurs.ErreurMetier;
import org.springframework.http.HttpStatus;

/** Une saisie refusée après la validation de forme : le champ en cause porte un message. */
public class SaisieRefusee extends ErreurMetier {

    private final String champ;
    private final String message;

    public SaisieRefusee(String champ, String message) {
        super(HttpStatus.BAD_REQUEST, "requete-invalide", "Requête invalide");
        this.champ = champ;
        this.message = message;
    }

    public String champ() {
        return champ;
    }

    public String message() {
        return message;
    }
}
