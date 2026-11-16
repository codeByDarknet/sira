package bf.sira.journal;

import bf.sira.commun.jose.JoseRefuse;
import bf.sira.journal.PointsDeControle.PointDeControle;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalcule toute la chaîne du journal et la confronte au dernier point de contrôle signé : une
 * ligne modifiée, supprimée ou insérée après coup rompt la chaîne à cet endroit.
 */
@Component
public class VerificationJournal {

    private static final Logger journalisation = LoggerFactory.getLogger(VerificationJournal.class);

    private final JdbcClient sql;
    private final PointsDeControle pointsDeControle;

    VerificationJournal(JdbcClient sql, PointsDeControle pointsDeControle) {
        this.sql = sql;
        this.pointsDeControle = pointsDeControle;
    }

    /**
     * @param lignes nombre de lignes recalculées
     * @param dernierHachage hachage de la dernière ligne, en hexadécimal
     * @param premiereRupture numéro de la première ligne qui ne correspond plus
     */
    public record Verification(
            boolean valide,
            long lignes,
            String dernierHachage,
            PointDeControle dernierPointDeControle,
            Long premiereRupture) {}

    // Une seule transaction en lecture : la chaîne et le point de contrôle sont vus au même
    // instant.
    @Transactional(readOnly = true)
    public Verification verifier() {
        Optional<PointDeControle> point = pointsDeControle.dernier();
        Parcours parcours = new Parcours(point.map(PointDeControle::ligne).orElse(0L));
        sql.sql(
                        "SELECT "
                                + Chaine.COLONNES
                                + ", hachage_precedent, hachage FROM journal ORDER BY numero")
                .query(parcours);

        Long rupture = parcours.rupture;
        if (rupture == null && point.isPresent() && !certifie(point.get(), parcours.auPoint)) {
            rupture = point.get().ligne();
        }
        return new Verification(
                rupture == null,
                parcours.lignes,
                parcours.lignes == 0 ? null : HexFormat.of().formatHex(parcours.precedent),
                point.orElse(null),
                rupture);
    }

    private boolean certifie(PointDeControle point, byte[] hachageRecalcule) {
        try {
            return hachageRecalcule != null
                    && MessageDigest.isEqual(
                            pointsDeControle.hachageCertifie(point), hachageRecalcule);
        } catch (JoseRefuse | IllegalStateException | IllegalArgumentException e) {
            journalisation.warn("Point de contrôle du journal refusé : {}", e.getMessage());
            return false;
        }
    }

    /** Suit la chaîne ligne après ligne et retient le premier endroit où elle casse. */
    private static final class Parcours implements RowCallbackHandler {

        private final long lignePoint;
        private byte[] precedent = Chaine.ORIGINE;
        private byte[] auPoint;
        private long lignes;
        private Long rupture;

        Parcours(long lignePoint) {
            this.lignePoint = lignePoint;
        }

        @Override
        public void processRow(ResultSet ligne) throws SQLException {
            lignes++;
            long numero = ligne.getLong("numero");
            byte[] hachage = ligne.getBytes("hachage");
            boolean intacte =
                    numero == lignes
                            && MessageDigest.isEqual(ligne.getBytes("hachage_precedent"), precedent)
                            && MessageDigest.isEqual(Chaine.hachage(precedent, ligne), hachage);
            if (!intacte && rupture == null) {
                rupture = Math.min(numero, lignes);
            }
            precedent = hachage;
            if (numero == lignePoint) {
                auPoint = hachage;
            }
        }
    }
}
