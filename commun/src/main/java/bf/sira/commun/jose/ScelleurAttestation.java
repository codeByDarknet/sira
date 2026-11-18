package bf.sira.commun.jose;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse.Motif;
import bf.sira.commun.pki.CertificatRefuse;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.pki.UsageCle;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.List;

/**
 * Côté registre : signe une attestation avec le cachet, puis la chiffre pour le certificat de
 * chiffrement du service. Celui qui transporte l'attestation ne peut pas la lire.
 */
public final class ScelleurAttestation {

    private final Signataire signataire;
    private final X509Certificate autorite;
    private final Clock horloge;

    public ScelleurAttestation(Signataire signataire, X509Certificate autorite, Clock horloge) {
        this.signataire = signataire;
        this.autorite = autorite;
        this.horloge = horloge;
    }

    /**
     * Un destinataire dont le certificat de chiffrement a été validé : on peut sceller pour lui.
     */
    public static final class Destinataire {

        private final IdentiteMembre identite;
        private final ECPublicKey cle;

        private Destinataire(IdentiteMembre identite, ECPublicKey cle) {
            this.identite = identite;
            this.cle = cle;
        }

        public IdentiteMembre identite() {
            return identite;
        }
    }

    /**
     * Valide le certificat de chiffrement avant tout travail : le registre n'est consulté que pour
     * un destinataire à qui l'on pourra effectivement répondre.
     *
     * @param certificatChiffrementPem certificat de chiffrement reçu dans la requête
     * @param service le service nommé dans la requête signée par la passerelle
     * @throws JoseRefuse si le certificat ne remonte pas à l'autorité Sira, n'est pas un certificat
     *     de chiffrement, ou n'appartient pas au service : on ne chiffre alors pour personne
     */
    public Destinataire destinataire(String certificatChiffrementPem, IdentiteMembre service) {
        return new Destinataire(service, cleDeChiffrement(certificatChiffrementPem, service));
    }

    /** Raccourci de {@link #destinataire} puis {@link #sceller(JWTClaimsSet, Destinataire)}. */
    public String sceller(
            JWTClaimsSet revendications,
            String certificatChiffrementPem,
            IdentiteMembre destinataire) {
        return sceller(revendications, destinataire(certificatChiffrementPem, destinataire));
    }

    /**
     * @return le JWE au format compact, lisible du seul destinataire
     */
    public String sceller(JWTClaimsSet revendications, Destinataire destinataire) {
        if (revendications.getAudience() == null
                || !revendications.getAudience().equals(List.of(destinataire.identite().uri()))) {
            throw new IllegalArgumentException("« aud » doit être le seul destinataire");
        }
        ECPublicKey cleDestinataire = destinataire.cle;
        String jws = signataire.signer(TypesJose.ATTESTATION, revendications);
        JWEHeader enTete =
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM)
                        .contentType("JWT")
                        .build();
        JWEObject jwe = new JWEObject(enTete, new Payload(jws));
        try {
            jwe.encrypt(new ECDHEncrypter(cleDestinataire));
        } catch (JOSEException e) {
            throw new IllegalStateException("chiffrement impossible", e);
        }
        return jwe.serialize();
    }

    private ECPublicKey cleDeChiffrement(String pem, IdentiteMembre destinataire) {
        X509Certificate certificat;
        try {
            List<X509Certificate> chaine = Certificats.lirePem(pem);
            Certificats.validerChaine(chaine, autorite, horloge.instant());
            Certificats.validerUsage(chaine.getFirst(), UsageCle.CHIFFREMENT);
            certificat = chaine.getFirst();
        } catch (CertificatRefuse e) {
            throw new JoseRefuse(Motif.CERTIFICAT, "certificat de chiffrement refusé", e);
        }
        boolean appartientAuDestinataire =
                Certificats.uriSan(certificat)
                        .flatMap(IdentiteMembre::lire)
                        .filter(destinataire::equals)
                        .isPresent();
        if (!appartientAuDestinataire) {
            throw new JoseRefuse(
                    Motif.IDENTITE, "le certificat de chiffrement n'est pas celui du destinataire");
        }
        if (!(certificat.getPublicKey() instanceof ECPublicKey cle)) {
            throw new JoseRefuse(Motif.CERTIFICAT, "clé de chiffrement d'un type refusé");
        }
        return cle;
    }
}
