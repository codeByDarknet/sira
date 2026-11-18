package bf.sira.journal;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Le chaînage du journal : {@code hachage = SHA-256(hachage_precedent ‖ contenu canonique)}. Le
 * contenu canonique est la suite des colonnes, dans un ordre fixe, chacune précédée de sa longueur
 * : deux lignes différentes ne peuvent pas produire les mêmes octets.
 */
final class Chaine {

    /** Hachage qui précède la première ligne. */
    static final byte[] ORIGINE = new byte[32];

    static final String COLONNES =
            "numero, id, horodatage, type, resultat, citoyen, service, registre, jeu, niveau,"
                    + " demarche, finalite, canal, id_echange, demande, autorisation, detail,"
                    + " acteur, objet";

    private static final int NOMBRE_COLONNES = COLONNES.split(",").length;
    private static final int COLONNE_HORODATAGE = 2;
    private static final int ABSENT = -1;

    private Chaine() {}

    /** La précision que la base conserve : au-delà, le recalcul ne retrouverait pas la ligne. */
    static Instant arrondir(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    static byte[] hachage(byte[] precedent, Object... colonnes) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(precedent);
            sha256.update(canonique(colonnes));
            return sha256.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Recalcule le hachage d'une ligne lue avec {@link #COLONNES}, dans cet ordre. */
    static byte[] hachage(byte[] precedent, ResultSet ligne) throws SQLException {
        Object[] colonnes = new Object[NOMBRE_COLONNES];
        for (int i = 0; i < NOMBRE_COLONNES; i++) {
            colonnes[i] =
                    i == COLONNE_HORODATAGE
                            ? ligne.getObject(i + 1, OffsetDateTime.class).toInstant()
                            : ligne.getObject(i + 1);
        }
        return hachage(precedent, colonnes);
    }

    private static byte[] canonique(Object... colonnes) {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        for (Object colonne : colonnes) {
            if (colonne == null) {
                octets.writeBytes(longueur(ABSENT));
            } else {
                byte[] valeur = String.valueOf(colonne).getBytes(StandardCharsets.UTF_8);
                octets.writeBytes(longueur(valeur.length));
                octets.writeBytes(valeur);
            }
        }
        return octets.toByteArray();
    }

    private static byte[] longueur(int longueur) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(longueur).array();
    }
}
