package bf.sira.commun.jose;

/**
 * Un objet signé ou chiffré est refusé. Le motif sert au journal et aux tests ; il ne doit pas être
 * renvoyé tel quel à l'appelant.
 */
public class JoseRefuse extends RuntimeException {

    public enum Motif {
        FORMAT,
        CERTIFICAT,
        IDENTITE,
        SIGNATURE,
        AUDIENCE,
        VALIDITE,
        REJEU,
        DECHIFFREMENT
    }

    private final Motif motif;

    public JoseRefuse(Motif motif, String message) {
        super(message);
        this.motif = motif;
    }

    public JoseRefuse(Motif motif, String message, Throwable cause) {
        super(message, cause);
        this.motif = motif;
    }

    public Motif motif() {
        return motif;
    }
}
