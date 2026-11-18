package bf.sira.demo.registre.configuration;

import bf.sira.commun.erreurs.ErreurMetier;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Une saisie d'agent refusée : chaque champ en cause porte un message affichable tel quel. */
public class SaisieRefusee extends ErreurMetier {

    private final transient Map<String, String> erreurs;

    public SaisieRefusee(Map<String, String> erreurs) {
        super(
                HttpStatus.BAD_REQUEST,
                "requete-invalide",
                "Requête invalide",
                "Corrigez les champs signalés, puis enregistrez de nouveau.");
        this.erreurs = Map.copyOf(erreurs);
    }

    public Map<String, String> erreurs() {
        return erreurs;
    }
}
