package bf.sira.demo.registre.personnes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Une personne du registre. La fiche est un objet JSON plat (textes, nombres, booléens), dont les
 * champs sont décrits par la configuration du registre.
 */
public record Personne(UUID id, String identifiant, Map<String, Object> fiche, Instant modifieeLe) {

    public static final String IDENTIFIANT = "identifiant_unique";
    public static final String NOM = "nom";
    public static final String PRENOMS = "prenoms";
    public static final String DATE_NAISSANCE = "date_naissance";

    /** Douze chiffres : toute autre forme ne peut désigner personne. */
    public static boolean identifiantPlausible(String identifiant) {
        return identifiant != null && identifiant.matches("[0-9]{12}");
    }
}
