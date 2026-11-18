package bf.sira.commun.identite;

/** Autorités accordées à un membre authentifié par son certificat. */
public final class Autorites {

    public static final String SERVICE = "MEMBRE_SERVICE";
    public static final String REGISTRE = "MEMBRE_REGISTRE";
    public static final String PASSERELLE = "MEMBRE_SOCLE_PASSERELLE";
    public static final String ESPACE_CITOYEN = "MEMBRE_SOCLE_ESPACE_CITOYEN";
    public static final String CONSOLE = "MEMBRE_SOCLE_CONSOLE";

    private Autorites() {}
}
