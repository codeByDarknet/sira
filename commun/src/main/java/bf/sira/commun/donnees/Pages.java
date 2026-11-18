package bf.sira.commun.donnees;

import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Exécute une requête de liste : la page demandée et le total, avec les mêmes filtres. Le serveur
 * qui a une base de données en déclare un composant.
 */
public class Pages {

    private final JdbcClient sql;

    public Pages(JdbcClient sql) {
        this.sql = sql;
    }

    /**
     * @param colonnes ce qui suit {@code SELECT}
     * @param source ce qui suit {@code FROM}, filtres compris ; ses paramètres sont nommés
     * @param ordre ce qui suit {@code ORDER BY}
     */
    public <T> Page<T> lire(
            String colonnes,
            String source,
            String ordre,
            Map<String, ?> parametres,
            Pagination pagination,
            RowMapper<T> lecteur) {
        long total =
                sql.sql("SELECT count(*) FROM " + source)
                        .params(parametres)
                        .query(Long.class)
                        .single();
        var elements =
                sql.sql(
                                "SELECT "
                                        + colonnes
                                        + " FROM "
                                        + source
                                        + " ORDER BY "
                                        + ordre
                                        + " LIMIT :limite OFFSET :decalage")
                        .params(parametres)
                        .param("limite", pagination.taille())
                        .param("decalage", pagination.decalage())
                        .query(lecteur)
                        .list();
        return new Page<>(elements, pagination.page(), pagination.taille(), total);
    }
}
