package bf.sira.passerelle.config;

/** Formes admises des codes du référentiel. */
public final class Formes {

    /** Code d'un membre, d'un registre ou d'une démarche : celui d'une identité de certificat. */
    public static final String CODE = "[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?";

    /** Code d'un jeu : {@code <registre>.<nom>}. */
    public static final String CODE_JEU = "[a-z0-9][a-z0-9.-]{0,126}[a-z0-9]";

    private Formes() {}
}
