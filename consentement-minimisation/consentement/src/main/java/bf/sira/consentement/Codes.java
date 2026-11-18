package bf.sira.consentement;

import bf.sira.commun.crypto.Pseudonymes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Les codes remis aux services et aux citoyens. Tous viennent de {@link SecureRandom}, et aucun
 * n'est conservé en clair : la base ne garde que leur empreinte. Les codes courts et de
 * vérification, petits, sont pris par HMAC sous la clé de la passerelle (contrat §5.9), pour qu'une
 * copie de la base ne permette pas de les retrouver en les essayant tous.
 */
@Component
public class Codes {

    /** Sans I, O ni Q : un code se lit sur un écran et se tape sur un autre. */
    private static final String LETTRES = "ABCDEFGHJKLMNPRSTUVWXYZ";

    private static final int TAILLE_GROUPE = 4;
    private static final int OCTETS_CODE_RETOUR = 32;

    private final SecureRandom aleatoire;
    private final Pseudonymes cle;

    Codes(SecureRandom aleatoire, Pseudonymes cle) {
        this.aleatoire = aleatoire;
        this.cle = cle;
    }

    /** Code que le citoyen tape dans son espace : {@code KLPM-4821}. */
    public String codeCourt() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < TAILLE_GROUPE; i++) {
            code.append(LETTRES.charAt(aleatoire.nextInt(LETTRES.length())));
        }
        code.append('-');
        for (int i = 0; i < TAILLE_GROUPE; i++) {
            code.append(aleatoire.nextInt(10));
        }
        return code.toString();
    }

    /** Code à 6 chiffres que le citoyen compare entre l'écran du service et l'écran d'accord. */
    public String codeVerification() {
        return String.format(Locale.ROOT, "%06d", aleatoire.nextInt(1_000_000));
    }

    /** Code remis au seul navigateur qui a donné l'accord (canal REDIRECTION). */
    public String codeRetour() {
        byte[] octets = new byte[OCTETS_CODE_RETOUR];
        aleatoire.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** Empreinte d'un code court tel que le citoyen l'a tapé. */
    public String empreinteCodeCourt(String saisi) {
        return cle.calculer("code-court", normaliser(saisi));
    }

    /** Empreinte d'un code de vérification tel que le citoyen l'a tapé. */
    public String empreinteCodeVerification(String saisi) {
        return cle.calculer("code-verification", saisi.strip());
    }

    /** Un code court tel qu'il a pu être tapé : casse, espaces et tiret indifférents. */
    static String normaliser(String codeCourt) {
        return codeCourt.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static String empreinte(String valeur) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
