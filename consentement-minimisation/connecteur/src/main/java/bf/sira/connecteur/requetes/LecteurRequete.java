package bf.sira.connecteur.requetes;

import bf.sira.commun.erreurs.ErreurMetier;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** Lit et valide le contenu d'une requête dont la signature vient d'être vérifiée. */
@Component
class LecteurRequete {

    private static final JsonMapper JSON =
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build();

    private final Validator validateur;

    LecteurRequete(Validator validateur) {
        this.validateur = validateur;
    }

    /**
     * @throws ErreurMetier (400) si une revendication manque ou est mal formée ; l'erreur ne cite
     *     aucune valeur reçue
     */
    Requete lire(JWTClaimsSet revendications) {
        Requete requete;
        try {
            requete = JSON.convertValue(revendications.toJSONObject(), Requete.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw invalide();
        }
        if (!validateur.validate(requete).isEmpty()) {
            throw invalide();
        }
        return requete;
    }

    private static ErreurMetier invalide() {
        return new ErreurMetier(HttpStatus.BAD_REQUEST, "requete-invalide", "Requête invalide");
    }
}
