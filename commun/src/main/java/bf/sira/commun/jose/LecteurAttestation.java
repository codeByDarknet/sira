package bf.sira.commun.jose;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse.Motif;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;

/**
 * Côté service : déchiffre une attestation avec la clé de chiffrement du service, puis vérifie la
 * signature du registre.
 */
public final class LecteurAttestation {

    private final ECDHDecrypter dechiffreur;
    private final IdentiteMembre identite;
    private final VerificateurJws verificateur;

    public LecteurAttestation(Bicle chiffrement, VerificateurJws verificateur) {
        if (!(chiffrement.clePrivee() instanceof ECPrivateKey cle)) {
            throw new IllegalArgumentException("la clé de chiffrement doit être une clé EC P-256");
        }
        try {
            this.dechiffreur = new ECDHDecrypter(cle);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("clé de chiffrement inutilisable", e);
        }
        this.identite =
                Certificats.uriSan(chiffrement.certificat())
                        .flatMap(IdentiteMembre::lire)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "le certificat de chiffrement ne porte pas"
                                                + " d'identité Sira"));
        this.verificateur = verificateur;
    }

    /**
     * @param registre le registre qui détient le jeu attendu : lui seul peut l'attester
     * @return les revendications signées par ce registre (contrat §8)
     * @throws JoseRefuse si l'attestation n'est pas chiffrée pour ce service, ou si sa signature,
     *     son émetteur, son audience ou sa validité sont refusés
     */
    public JWTClaimsSet ouvrir(String jweCompact, IdentiteMembre registre) {
        if (registre == null || registre.type() != IdentiteMembre.Type.REGISTRE) {
            throw new IllegalArgumentException("le registre attendu est obligatoire");
        }
        JWEObject jwe;
        try {
            jwe = JWEObject.parse(jweCompact);
        } catch (ParseException | RuntimeException e) {
            throw new JoseRefuse(Motif.FORMAT, "JWE illisible", e);
        }
        if (!JWEAlgorithm.ECDH_ES_A256KW.equals(jwe.getHeader().getAlgorithm())
                || !EncryptionMethod.A256GCM.equals(jwe.getHeader().getEncryptionMethod())) {
            throw new JoseRefuse(Motif.FORMAT, "algorithme de chiffrement refusé");
        }
        try {
            jwe.decrypt(dechiffreur);
        } catch (JOSEException | RuntimeException e) {
            throw new JoseRefuse(Motif.DECHIFFREMENT, "attestation indéchiffrable", e);
        }
        return verificateur.verifier(
                jwe.getPayload().toString(), Attentes.attestation(identite, registre));
    }
}
