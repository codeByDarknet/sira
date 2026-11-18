package bf.sira.commun.identite;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L'identité d'un membre : l'URI du champ SAN de son certificat, de la forme {@code
 * urn:sira:<type>:<code>}.
 */
public record IdentiteMembre(Type type, String code) {

    public enum Type {
        SOCLE,
        SERVICE,
        REGISTRE
    }

    // Déclarés avant les constantes : leur construction valide déjà le code.
    private static final Pattern CODE = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?");
    private static final Pattern URI =
            Pattern.compile("urn:sira:(socle|service|registre):(" + CODE.pattern() + ")");

    public static final IdentiteMembre PASSERELLE = new IdentiteMembre(Type.SOCLE, "passerelle");
    public static final IdentiteMembre ESPACE_CITOYEN =
            new IdentiteMembre(Type.SOCLE, "espace-citoyen");
    public static final IdentiteMembre CONSOLE = new IdentiteMembre(Type.SOCLE, "console");

    /** Le fournisseur d'identité (Keycloak), joint en mTLS pour ses clés et son point de jetons. */
    public static final IdentiteMembre FOURNISSEUR_IDENTITE =
            new IdentiteMembre(Type.SOCLE, "identite");

    public IdentiteMembre {
        if (type == null || code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("identité de membre invalide");
        }
    }

    public static IdentiteMembre service(String code) {
        return new IdentiteMembre(Type.SERVICE, code);
    }

    public static IdentiteMembre registre(String code) {
        return new IdentiteMembre(Type.REGISTRE, code);
    }

    /** Lit une URI ; vide si elle n'est pas, à la lettre, une identité Sira. */
    public static Optional<IdentiteMembre> lire(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        Matcher m = URI.matcher(uri);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(
                new IdentiteMembre(Type.valueOf(m.group(1).toUpperCase(Locale.ROOT)), m.group(2)));
    }

    public String uri() {
        return "urn:sira:" + type.name().toLowerCase(Locale.ROOT) + ":" + code;
    }

    /**
     * L'autorité Spring Security correspondante : {@code MEMBRE_SERVICE}, {@code MEMBRE_REGISTRE},
     * ou {@code MEMBRE_SOCLE_<CODE>} pour un composant du socle (ex. {@code
     * MEMBRE_SOCLE_PASSERELLE}).
     */
    public String autorite() {
        return switch (type) {
            case SERVICE -> Autorites.SERVICE;
            case REGISTRE -> Autorites.REGISTRE;
            case SOCLE -> "MEMBRE_SOCLE_" + code.toUpperCase(Locale.ROOT).replace('-', '_');
        };
    }

    @Override
    public String toString() {
        return uri();
    }
}
