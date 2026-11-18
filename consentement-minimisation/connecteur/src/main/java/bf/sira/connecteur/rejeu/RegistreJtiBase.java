package bf.sira.connecteur.rejeu;

import bf.sira.commun.jose.RegistreJti;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Mémoire des requêtes déjà reçues, en base : elle survit à un redémarrage et se partage entre
 * plusieurs instances du connecteur d'un même registre.
 */
@Component
public class RegistreJtiBase implements RegistreJti {

    private final JdbcClient jdbc;
    private final Clock horloge;

    public RegistreJtiBase(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public boolean consommer(String cle, Instant expiration) {
        try {
            jdbc.sql("DELETE FROM jti_vu WHERE expire_le <= :maintenant")
                    .param("maintenant", horodatage(horloge.instant()))
                    .update();
            // La clé primaire tranche entre deux présentations simultanées : une seule insère.
            return jdbc.sql(
                                    "INSERT INTO jti_vu (cle, expire_le) VALUES (:cle, :expire_le)"
                                            + " ON CONFLICT (cle) DO NOTHING")
                            .param("cle", cle)
                            .param("expire_le", horodatage(expiration))
                            .update()
                    == 1;
        } catch (DataAccessException e) {
            // Sans mémoire, on ne peut pas exclure un rejeu : la requête est refusée.
            return false;
        }
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
