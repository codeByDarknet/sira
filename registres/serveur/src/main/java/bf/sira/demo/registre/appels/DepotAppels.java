package bf.sira.demo.registre.appels;

import bf.sira.demo.registre.Page;
import bf.sira.demo.registre.pannes.Panne;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DepotAppels {

    /** L'historique sert à la démonstration : seuls les derniers appels sont gardés. */
    private static final int CONSERVES = 2000;

    private final JdbcClient base;

    DepotAppels(JdbcClient base) {
        this.base = base;
    }

    public void consigner(
            Appel.Type type, Panne.Mode mode, Integer statut, int dureeMs, String origine) {
        base.sql(
                        """
                        INSERT INTO appel_recu (type, mode, statut, duree_ms, origine)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(type.name(), mode.name(), statut, dureeMs, origine)
                .update();
        // Seuls les derniers appels sont gardés.
        base.sql(
                        """
                        DELETE FROM appel_recu WHERE id IN (
                            SELECT id FROM appel_recu ORDER BY horodatage DESC OFFSET ?)
                        """)
                .param(CONSERVES)
                .update();
    }

    Page<Appel> lister(int page, int taille) {
        long total = base.sql("SELECT count(*) FROM appel_recu").query(Long.class).single();
        List<Appel> elements =
                base.sql(
                                """
                                SELECT id, horodatage, type, mode, statut, duree_ms, origine
                                  FROM appel_recu ORDER BY horodatage DESC, id LIMIT ? OFFSET ?
                                """)
                        .params(taille, Page.decalage(page, taille))
                        .query((ligne, numero) ->
                                new Appel(
                                        ligne.getObject("id", UUID.class),
                                        ligne.getTimestamp("horodatage").toInstant(),
                                        Appel.Type.valueOf(ligne.getString("type")),
                                        Panne.Mode.valueOf(ligne.getString("mode")),
                                        ligne.getObject("statut", Integer.class),
                                        ligne.getInt("duree_ms"),
                                        ligne.getString("origine")))
                        .list();
        return new Page<>(elements, page, taille, total);
    }
}
