package bf.sira.commun.jose;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Signe au nom du membre avec son cachet : JWS ES256 dont l'en-tête {@code x5c} porte le
 * certificat.
 */
public final class Signataire {

    private final ECDSASigner signeur;
    private final List<Base64> x5c;
    private final IdentiteMembre identite;

    public Signataire(Bicle cachet) {
        if (!(cachet.clePrivee() instanceof ECPrivateKey cle)) {
            throw new IllegalArgumentException("le cachet doit être une clé ECDSA P-256");
        }
        try {
            this.signeur = new ECDSASigner(cle, Curve.P_256);
            List<Base64> chaine = new ArrayList<>();
            for (X509Certificate certificat : cachet.chaine()) {
                chaine.add(Base64.encode(certificat.getEncoded()));
            }
            this.x5c = List.copyOf(chaine);
        } catch (JOSEException | CertificateEncodingException e) {
            throw new IllegalArgumentException("cachet inutilisable", e);
        }
        this.identite =
                Certificats.uriSan(cachet.certificat())
                        .flatMap(IdentiteMembre::lire)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "le certificat de cachet ne porte pas d'identité Sira"));
    }

    /** L'identité du signataire, à placer dans la revendication {@code iss}. */
    public IdentiteMembre identite() {
        return identite;
    }

    /**
     * @param typ une constante de {@link TypesJose}
     * @return le JWS au format compact
     */
    public String signer(String typ, JWTClaimsSet revendications) {
        if (!identite.uri().equals(revendications.getIssuer())) {
            throw new IllegalArgumentException("« iss » doit être l'identité du signataire");
        }
        JWSHeader enTete =
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(typ))
                        .x509CertChain(x5c)
                        .build();
        SignedJWT jws = new SignedJWT(enTete, revendications);
        try {
            jws.sign(signeur);
        } catch (JOSEException e) {
            throw new IllegalStateException("signature impossible", e);
        }
        return jws.serialize();
    }
}
