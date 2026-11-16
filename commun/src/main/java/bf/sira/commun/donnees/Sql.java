package bf.sira.commun.donnees;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Passage des types Java aux types PostgreSQL que le pilote ne convertit pas seul. */
public final class Sql {

    private Sql() {}

    /** Un instant, sous la forme que le pilote accepte pour une colonne {@code timestamptz}. */
    public static OffsetDateTime date(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet ligne, String colonne) throws SQLException {
        OffsetDateTime date = ligne.getObject(colonne, OffsetDateTime.class);
        return date == null ? null : date.toInstant();
    }

    public static String[] tableau(Collection<String> valeurs) {
        return valeurs == null ? null : valeurs.toArray(String[]::new);
    }

    public static List<String> liste(ResultSet ligne, String colonne) throws SQLException {
        Array tableau = ligne.getArray(colonne);
        return tableau == null ? null : List.of((String[]) tableau.getArray());
    }

    /** Un filtre facultatif : une chaîne vide vaut « pas de filtre ». */
    public static String filtre(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur;
    }

    /**
     * Paramètres nommés d'une requête ; contrairement à {@code Map.of}, une valeur peut manquer.
     */
    public static Map<String, Object> parametres(Object... paires) {
        Map<String, Object> parametres = new HashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            parametres.put((String) paires[i], paires[i + 1]);
        }
        return parametres;
    }
}
