package bf.sira.demo.registre.pannes;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DepotPanne {

    private final JdbcClient base;

    DepotPanne(JdbcClient base) {
        this.base = base;
    }

    public Panne lire() {
        return base.sql("SELECT mode, delai_ms, modifiee_le FROM panne")
                .query((ligne, numero) ->
                        new Panne(
                                Panne.Mode.valueOf(ligne.getString("mode")),
                                ligne.getInt("delai_ms"),
                                ligne.getTimestamp("modifiee_le").toInstant()))
                .single();
    }

    /** Change de mode ; sans délai fourni, le délai précédent est gardé. */
    void changer(Panne.Mode mode, Integer delaiMs) {
        base.sql(
                        """
                        UPDATE panne
                           SET mode = ?, delai_ms = COALESCE(?, delai_ms), modifiee_le = now()
                        """)
                .params(mode.name(), delaiMs)
                .update();
    }
}
