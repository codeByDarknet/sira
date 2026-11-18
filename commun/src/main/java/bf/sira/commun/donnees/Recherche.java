package bf.sira.commun.donnees;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

/**
 * Paramètres {@code ?recherche=&actif=&tri=&sens=} d'une liste : recherche dans le texte, filtre
 * sur l'état, colonne et sens du tri. Tout se fait côté serveur, sur toutes les lignes, avant la
 * pagination. Le tri n'accepte qu'une colonne de la liste blanche de l'écran ; sinon, l'ordre par
 * défaut s'applique.
 */
public record Recherche(
        @Size(max = 100) String recherche,
        @Pattern(regexp = "|true|false") String actif,
        @Size(max = 40) @Pattern(regexp = "|[a-z_]+") String tri,
        @Pattern(regexp = "|asc|desc") String sens) {

    /**
     * Motif {@code LIKE} en minuscules, caractères spéciaux neutralisés ; {@code null} sans
     * recherche.
     */
    public String motif() {
        if (recherche == null || recherche.isBlank()) {
            return null;
        }
        String echappe =
                recherche
                        .strip()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
        return "%" + echappe + "%";
    }

    public Boolean actifDemande() {
        return actif == null || actif.isBlank() ? null : Boolean.valueOf(actif);
    }

    /**
     * @param permises colonnes que l'écran peut trier
     * @param defaut ordre quand le tri demandé n'est pas permis
     * @return ce qui suit {@code ORDER BY}
     */
    public String ordre(List<String> permises, String defaut) {
        if (tri == null || !permises.contains(tri)) {
            return defaut;
        }
        return tri + ("desc".equals(sens) ? " DESC" : " ASC") + ", " + defaut;
    }

    /**
     * Condition SQL : le motif dans l'une des colonnes texte (paramètre {@code :motif}).
     *
     * @param colonnes colonnes fixées par le code, jamais lues d'une entrée
     */
    public static String dansLeTexte(List<String> colonnes) {
        return "(CAST(:motif AS text) IS NULL OR "
                + String.join(
                        " OR ", colonnes.stream().map(c -> "lower(" + c + ") LIKE :motif").toList())
                + ")";
    }

    public static Recherche aucune() {
        return new Recherche(null, null, null, null);
    }
}
