package bf.sira.commun.pki;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lecture et validation des certificats X.509 émis par l'autorité Sira. */
public final class Certificats {

    private static final int SAN_URI = 6;
    private static final int TAILLE_PEM_MAX = 32_768;

    private Certificats() {}

    /** Lit un ou plusieurs certificats au format PEM, dans l'ordre du texte. */
    public static List<X509Certificate> lirePem(String pem) {
        if (pem == null || pem.isBlank() || pem.length() > TAILLE_PEM_MAX) {
            throw new CertificatRefuse("certificat absent ou trop volumineux");
        }
        try {
            Collection<?> lus =
                    CertificateFactory.getInstance("X.509")
                            .generateCertificates(
                                    new ByteArrayInputStream(
                                            pem.getBytes(StandardCharsets.US_ASCII)));
            if (lus.isEmpty()) {
                throw new CertificatRefuse("aucun certificat dans le texte fourni");
            }
            return lus.stream().map(X509Certificate.class::cast).toList();
        } catch (CertificateException e) {
            throw new CertificatRefuse("certificat illisible", e);
        }
    }

    public static String versPem(X509Certificate certificat) {
        try {
            String corps =
                    Base64.getMimeEncoder(64, new byte[] {'\n'})
                            .encodeToString(certificat.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + corps + "\n-----END CERTIFICATE-----\n";
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * L'identité portée par le certificat : son unique SAN de type URI. Un certificat qui en porte
     * plusieurs est ambigu, donc sans identité.
     */
    public static Optional<String> uriSan(X509Certificate certificat) {
        try {
            Collection<List<?>> sans = certificat.getSubjectAlternativeNames();
            if (sans == null) {
                return Optional.empty();
            }
            List<String> uris =
                    sans.stream()
                            .filter(san -> Integer.valueOf(SAN_URI).equals(san.get(0)))
                            .map(san -> (String) san.get(1))
                            .toList();
            return uris.size() == 1 ? Optional.of(uris.getFirst()) : Optional.empty();
        } catch (CertificateParsingException e) {
            return Optional.empty();
        }
    }

    /** Empreinte SHA-256 du certificat en base64url, telle que portée par {@code cnf.x5t#S256}. */
    public static String empreinteSha256(X509Certificate certificat) {
        try {
            byte[] empreinte = MessageDigest.getInstance("SHA-256").digest(certificat.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(empreinte);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Vérifie que la chaîne (feuille en premier) remonte à l'autorité, à l'instant donné. La
     * révocation n'est pas consultée : les certificats de l'autorité interne ont une durée courte.
     */
    public static void validerChaine(
            List<X509Certificate> chaine, X509Certificate autorite, Instant instant) {
        if (chaine == null || chaine.isEmpty()) {
            throw new CertificatRefuse("chaîne de certificats vide");
        }
        // Une chaîne réduite à l'autorité serait valide « à vide » : la feuille doit être un
        // certificat final.
        if (chaine.getFirst().getBasicConstraints() != -1) {
            throw new CertificatRefuse("le certificat présenté est celui d'une autorité");
        }
        List<X509Certificate> sansAutorite =
                chaine.stream().filter(c -> !c.equals(autorite)).toList();
        try {
            CertPath chemin =
                    CertificateFactory.getInstance("X.509").generateCertPath(sansAutorite);
            PKIXParameters parametres = new PKIXParameters(Set.of(new TrustAnchor(autorite, null)));
            parametres.setRevocationEnabled(false);
            parametres.setDate(Date.from(instant));
            CertPathValidator.getInstance("PKIX").validate(chemin, parametres);
        } catch (GeneralSecurityException e) {
            throw new CertificatRefuse("la chaîne ne remonte pas à l'autorité Sira", e);
        }
    }

    /**
     * Refuse un certificat qui n'est pas émis pour cet usage : {@code keyUsage} absent ou autre, ou
     * certificat de transport (porteur d'un {@code extendedKeyUsage}).
     */
    public static void validerUsage(X509Certificate certificat, UsageCle usage) {
        boolean[] keyUsage = certificat.getKeyUsage();
        if (keyUsage == null
                || keyUsage.length <= usage.bitKeyUsage()
                || !keyUsage[usage.bitKeyUsage()]) {
            throw new CertificatRefuse("certificat non émis pour l'usage " + usage);
        }
        try {
            if (certificat.getExtendedKeyUsage() != null) {
                throw new CertificatRefuse(
                        "un certificat de transport ne sert pas à l'usage " + usage);
            }
        } catch (CertificateParsingException e) {
            throw new CertificatRefuse("certificat illisible", e);
        }
    }
}
