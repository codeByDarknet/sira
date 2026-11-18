package bf.sira.kit;

/**
 * La passerelle a refusé l'appel. Le {@code type} est celui du problème RFC 9457 renvoyé (ex.
 * {@code urn:sira:erreur:acces-refuse}).
 */
public class ErreurPasserelle extends RuntimeException {

    private final int statut;
    private final String type;

    public ErreurPasserelle(int statut, String type, String titre) {
        super("refus de la passerelle : " + statut + (titre != null ? " " + titre : ""));
        this.statut = statut;
        this.type = type;
    }

    public int statut() {
        return statut;
    }

    /** Le type du problème, ou {@code null} si la réponse n'en portait pas. */
    public String type() {
        return type;
    }
}
