package bf.sira.commun.jose;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse.Motif;
import bf.sira.commun.pki.CertificatRefuse;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.pki.UsageCle;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.X509CertChainUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Vérifie un JWS signé par le cachet d'un membre. La clé de vérification vient du certificat de
 * l'en-tête {@code x5c}, qui n'est cru que si sa chaîne remonte à l'autorité Sira et si son SAN est
 * l'identité attendue.
 */
public final class VerificateurJws {

    /** Écart d'horloge toléré entre deux machines. */
    static final Duration TOLERANCE = Duration.ofSeconds(5);

    private static final int TAILLE_JTI_MAX = 128;

    private final X509Certificate autorite;
    private final RegistreJti registreJti;
    private final Clock horloge;

    public VerificateurJws(X509Certificate autorite, RegistreJti registreJti, Clock horloge) {
        this.autorite = autorite;
        this.registreJti = registreJti;
        this.horloge = horloge;
    }

    /**
     * @return les revendications, une fois tout vérifié
     * @throws JoseRefuse au premier contrôle qui échoue
     */
    public JWTClaimsSet verifier(String jwsCompact, Attentes attentes) {
        SignedJWT jws = analyser(jwsCompact);
        JWSHeader enTete = jws.getHeader();
        if (!JWSAlgorithm.ES256.equals(enTete.getAlgorithm())) {
            throw new JoseRefuse(Motif.FORMAT, "algorithme de signature refusé");
        }
        if (!new JOSEObjectType(attentes.typ()).equals(enTete.getType())) {
            throw new JoseRefuse(Motif.FORMAT, "type d'objet inattendu");
        }

        Instant maintenant = horloge.instant();
        X509Certificate certificat = certificatDeConfiance(enTete.getX509CertChain(), maintenant);
        IdentiteMembre signataire = identite(certificat, attentes);
        verifierSignature(jws, certificat);

        JWTClaimsSet revendications = revendications(jws);
        if (!signataire.uri().equals(revendications.getIssuer())) {
            throw new JoseRefuse(Motif.IDENTITE, "« iss » n'est pas l'identité du signataire");
        }
        // Une seule audience : un objet adressé à plusieurs membres pourrait être rejoué chez l'un
        // d'eux.
        if (!List.of(attentes.audience().uri()).equals(revendications.getAudience())) {
            throw new JoseRefuse(Motif.AUDIENCE, "objet destiné à un autre membre");
        }
        verifierValidite(revendications, attentes, maintenant);
        verifierJti(revendications, attentes);
        return revendications;
    }

    private static SignedJWT analyser(String jwsCompact) {
        try {
            return SignedJWT.parse(jwsCompact);
        } catch (ParseException | RuntimeException e) {
            throw new JoseRefuse(Motif.FORMAT, "JWS illisible", e);
        }
    }

    private X509Certificate certificatDeConfiance(List<Base64> x5c, Instant maintenant) {
        if (x5c == null || x5c.isEmpty()) {
            throw new JoseRefuse(Motif.CERTIFICAT, "en-tête x5c absent");
        }
        try {
            List<X509Certificate> chaine = X509CertChainUtils.parse(x5c);
            Certificats.validerChaine(chaine, autorite, maintenant);
            Certificats.validerUsage(chaine.getFirst(), UsageCle.CACHET);
            return chaine.getFirst();
        } catch (ParseException | CertificatRefuse e) {
            throw new JoseRefuse(Motif.CERTIFICAT, "certificat du signataire refusé", e);
        }
    }

    private static IdentiteMembre identite(X509Certificate certificat, Attentes attentes) {
        IdentiteMembre signataire =
                Certificats.uriSan(certificat)
                        .flatMap(IdentiteMembre::lire)
                        .orElseThrow(() ->
                                new JoseRefuse(Motif.IDENTITE, "certificat sans identité Sira"));
        boolean attendu =
                signataire.type() == attentes.typeEmetteur()
                        && (attentes.emetteur() == null || attentes.emetteur().equals(signataire));
        if (!attendu) {
            throw new JoseRefuse(Motif.IDENTITE, "signataire inattendu");
        }
        return signataire;
    }

    private static void verifierSignature(SignedJWT jws, X509Certificate certificat) {
        if (!(certificat.getPublicKey() instanceof ECPublicKey cle)) {
            throw new JoseRefuse(Motif.CERTIFICAT, "clé de signature d'un type refusé");
        }
        try {
            if (!jws.verify(new ECDSAVerifier(cle))) {
                throw new JoseRefuse(Motif.SIGNATURE, "signature invalide");
            }
        } catch (JOSEException e) {
            throw new JoseRefuse(Motif.SIGNATURE, "signature invérifiable", e);
        }
    }

    private static JWTClaimsSet revendications(SignedJWT jws) {
        try {
            return jws.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new JoseRefuse(Motif.FORMAT, "revendications illisibles", e);
        }
    }

    private static void verifierValidite(
            JWTClaimsSet revendications, Attentes attentes, Instant maintenant) {
        Date emission = revendications.getIssueTime();
        Date expiration = revendications.getExpirationTime();
        if (emission == null || expiration == null) {
            throw new JoseRefuse(Motif.VALIDITE, "« iat » et « exp » obligatoires");
        }
        if (!maintenant.isBefore(expiration.toInstant().plus(TOLERANCE))) {
            throw new JoseRefuse(Motif.VALIDITE, "objet expiré");
        }
        if (emission.toInstant().isAfter(maintenant.plus(TOLERANCE))) {
            throw new JoseRefuse(Motif.VALIDITE, "objet émis dans le futur");
        }
        if (attentes.dureeMax() != null
                && Duration.between(emission.toInstant(), expiration.toInstant())
                                .compareTo(attentes.dureeMax())
                        > 0) {
            throw new JoseRefuse(Motif.VALIDITE, "durée de validité excessive");
        }
    }

    private void verifierJti(JWTClaimsSet revendications, Attentes attentes) {
        String jti = revendications.getJWTID();
        if (jti == null || jti.isBlank() || jti.length() > TAILLE_JTI_MAX) {
            throw new JoseRefuse(Motif.FORMAT, "« jti » absent ou invalide");
        }
        if (attentes.usageUnique()) {
            // L'échéance mémorisée couvre la tolérance d'horloge : tant que l'objet peut encore
            // être accepté, son identifiant reste connu.
            Instant echeance = revendications.getExpirationTime().toInstant().plus(TOLERANCE);
            if (!registreJti.consommer(revendications.getIssuer() + "|" + jti, echeance)) {
                throw new JoseRefuse(Motif.REJEU, "objet déjà présenté");
            }
        }
    }
}
