package bf.sira.commun.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Comparaison de secrets sans fuite par le temps de réponse. */
public final class TempsConstant {

    private TempsConstant() {}

    public static boolean egaux(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return egaux(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Les deux valeurs sont d'abord condensées : la comparaison porte toujours sur 32 octets et ne
     * révèle donc pas non plus la longueur du secret.
     */
    public static boolean egaux(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] condenseA = sha256.digest(a);
            byte[] condenseB = sha256.digest(b);
            return MessageDigest.isEqual(condenseA, condenseB);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
