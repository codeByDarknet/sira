package bf.sira.commun.jose;

/**
 * Valeurs de l'en-tête {@code typ} : un objet signé pour un usage n'est jamais accepté pour un
 * autre.
 */
public final class TypesJose {

    /** Requête de la passerelle vers un connecteur (contrat §8). */
    public static final String REQUETE = "sira-requete+jwt";

    /** Contenu signé d'une attestation (contrat §8). */
    public static final String ATTESTATION = "sira-attestation+jwt";

    /** Point de contrôle signé du journal (contrat §10). */
    public static final String POINT_DE_CONTROLE = "sira-journal+jwt";

    private TypesJose() {}
}
