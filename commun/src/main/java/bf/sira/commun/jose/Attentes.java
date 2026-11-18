package bf.sira.commun.jose;

import bf.sira.commun.identite.IdentiteMembre;
import java.time.Duration;

/**
 * Ce qu'un vérificateur exige d'un JWS.
 *
 * @param typ valeur exacte de l'en-tête {@code typ}
 * @param typeEmetteur type de membre autorisé à signer
 * @param emetteur identité exacte du signataire, ou {@code null} pour tout membre du type
 * @param audience seule identité admise dans {@code aud} : celle du vérificateur
 * @param usageUnique refuser un {@code jti} déjà vu
 * @param dureeMax durée de validité maximale tolérée, ou {@code null}
 */
public record Attentes(
        String typ,
        IdentiteMembre.Type typeEmetteur,
        IdentiteMembre emetteur,
        IdentiteMembre audience,
        boolean usageUnique,
        Duration dureeMax) {

    /** Durée de vie d'une requête passerelle → connecteur (contrat §5.4). */
    public static final Duration VALIDITE_REQUETE = Duration.ofSeconds(60);

    public Attentes {
        if (typ == null || typeEmetteur == null || audience == null) {
            throw new IllegalArgumentException("typ, type d'émetteur et audience obligatoires");
        }
        if (emetteur != null && emetteur.type() != typeEmetteur) {
            throw new IllegalArgumentException("émetteur incohérent avec son type");
        }
    }

    /**
     * Requête signée par la passerelle, reçue par le connecteur du registre {@code destinataire}.
     */
    public static Attentes requete(IdentiteMembre destinataire) {
        return new Attentes(
                TypesJose.REQUETE,
                IdentiteMembre.Type.SOCLE,
                IdentiteMembre.PASSERELLE,
                destinataire,
                true,
                VALIDITE_REQUETE);
    }

    /**
     * Attestation signée par le registre {@code registre} pour le service {@code destinataire}.
     * Elle peut être relue plusieurs fois pendant sa validité : pas d'usage unique.
     */
    public static Attentes attestation(IdentiteMembre destinataire, IdentiteMembre registre) {
        return new Attentes(
                TypesJose.ATTESTATION,
                IdentiteMembre.Type.REGISTRE,
                registre,
                destinataire,
                false,
                null);
    }
}
