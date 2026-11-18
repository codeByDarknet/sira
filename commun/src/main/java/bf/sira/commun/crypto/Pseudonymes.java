package bf.sira.commun.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pseudonyme d'un citoyen propre à un secteur (un service, ou le journal) : HMAC-SHA-256(clé,
 * domaine ‖ secteur ‖ identifiant unique), chaque partie précédée de sa longueur. Deux secteurs ne
 * peuvent pas rapprocher leurs pseudonymes, et sans la clé nul ne peut les recalculer.
 */
public final class Pseudonymes {

    private static final String ALGORITHME = "HmacSHA256";
    private static final byte[] DOMAINE = "sira-pseudonyme-v1".getBytes(StandardCharsets.UTF_8);
    private static final int TAILLE_CLE_MIN = 32;

    private final SecretKeySpec cle;

    public Pseudonymes(byte[] cle) {
        if (cle == null || cle.length < TAILLE_CLE_MIN) {
            throw new IllegalArgumentException("clé de pseudonymisation trop courte");
        }
        this.cle = new SecretKeySpec(cle, ALGORITHME);
    }

    /** Lit la clé (octets bruts, 32 au moins) dans un fichier généré au premier démarrage. */
    public static Pseudonymes depuisFichier(Path fichier) {
        try {
            return new Pseudonymes(Files.readAllBytes(fichier));
        } catch (IOException e) {
            throw new UncheckedIOException("clé de pseudonymisation illisible : " + fichier, e);
        }
    }

    public String calculer(String secteur, String identifiantUnique) {
        if (secteur == null
                || secteur.isEmpty()
                || identifiantUnique == null
                || identifiantUnique.isEmpty()) {
            throw new IllegalArgumentException("secteur et identifiant unique obligatoires");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(cle);
            ajouter(mac, DOMAINE);
            ajouter(mac, secteur.getBytes(StandardCharsets.UTF_8));
            ajouter(mac, identifiantUnique.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void ajouter(Mac mac, byte[] partie) {
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(partie.length).array());
        mac.update(partie);
    }
}
