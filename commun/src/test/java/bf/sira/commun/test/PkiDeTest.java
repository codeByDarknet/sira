package bf.sira.commun.test;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;

/**
 * Autorité de certification jetable pour les tests : émet, en mémoire, les trois certificats d'un
 * membre avec les mêmes usages que l'autorité Sira réelle. Rien n'est jamais écrit dans le dépôt.
 */
public final class PkiDeTest {

    private static final SecureRandom ALEA = new SecureRandom();

    private final KeyPair cles;
    private final X500Name nom;
    private final X509Certificate autorite;

    private PkiDeTest() {
        this.cles = paire();
        this.nom = new X500Name("CN=Autorité de test " + new BigInteger(32, ALEA).toString(16));
        Instant maintenant = Instant.now();
        X509v3CertificateBuilder constructeur =
                constructeur(nom, cles, maintenant.minus(VALIDITE), maintenant.plus(VALIDITE));
        try {
            constructeur.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            constructeur.addExtension(
                    Extension.keyUsage,
                    true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.autorite = signer(constructeur);
    }

    private static final Duration VALIDITE = Duration.ofDays(1);

    /** Une autorité neuve, étrangère à toutes les autres. */
    public static PkiDeTest creer() {
        return new PkiDeTest();
    }

    public X509Certificate autorite() {
        return autorite;
    }

    /**
     * Certificat de transport : sert au mTLS, côté client comme côté serveur.
     *
     * @param nomsDns noms sous lesquels le membre est joint ({@code localhost} et 127.0.0.1 sont
     *     toujours ajoutés)
     */
    public Bicle tls(IdentiteMembre membre, String... nomsDns) {
        List<GeneralName> noms = new ArrayList<>();
        noms.add(new GeneralName(GeneralName.uniformResourceIdentifier, membre.uri()));
        noms.add(new GeneralName(GeneralName.dNSName, "localhost"));
        noms.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
        for (String nomDns : nomsDns) {
            noms.add(new GeneralName(GeneralName.dNSName, nomDns));
        }
        return emettre(
                membre.code() + " tls",
                noms,
                new KeyUsage(KeyUsage.digitalSignature),
                new ExtendedKeyUsage(
                        new KeyPurposeId[] {
                            KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth
                        }),
                Instant.now());
    }

    /** Certificat de cachet : signature des requêtes et des attestations. */
    public Bicle cachet(IdentiteMembre membre) {
        return cachet(membre.uri(), Instant.now());
    }

    /** Certificat de cachet dont la validité est centrée sur l'instant donné. */
    public Bicle cachet(String uriSan, Instant instant) {
        return emettre(
                "cachet",
                List.of(new GeneralName(GeneralName.uniformResourceIdentifier, uriSan)),
                new KeyUsage(KeyUsage.digitalSignature),
                null,
                instant);
    }

    /** Certificat sans extension {@code keyUsage} : il ne vaut pour aucun usage. */
    public Bicle sansUsage(IdentiteMembre membre) {
        return emettre(
                membre.code() + " sans usage",
                List.of(new GeneralName(GeneralName.uniformResourceIdentifier, membre.uri())),
                null,
                null,
                Instant.now());
    }

    /** Certificat de chiffrement : réception des attestations. */
    public Bicle chiffrement(IdentiteMembre membre) {
        return emettre(
                membre.code() + " chiffrement",
                List.of(new GeneralName(GeneralName.uniformResourceIdentifier, membre.uri())),
                new KeyUsage(KeyUsage.keyAgreement),
                null,
                Instant.now());
    }

    /**
     * Écrit le répertoire de clés d'un membre, tel que monté dans son conteneur : {@code
     * tls.crt/.key}, {@code cachet.crt/.key}, {@code chiffrement.crt/.key} et {@code ca.crt}.
     */
    public Path ecrire(Path repertoire, IdentiteMembre membre, String... nomsDns) {
        try {
            Files.createDirectories(repertoire);
            Files.writeString(repertoire.resolve("ca.crt"), Certificats.versPem(autorite));
            ecrire(repertoire, "tls", tls(membre, nomsDns));
            ecrire(repertoire, "cachet", cachet(membre));
            ecrire(repertoire, "chiffrement", chiffrement(membre));
            return repertoire;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void ecrire(Path repertoire, String nom, Bicle bicle) throws IOException {
        Files.writeString(
                repertoire.resolve(nom + ".crt"), Certificats.versPem(bicle.certificat()));
        String cle =
                Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(bicle.clePrivee().getEncoded());
        Files.writeString(
                repertoire.resolve(nom + ".key"),
                "-----BEGIN PRIVATE KEY-----\n" + cle + "\n-----END PRIVATE KEY-----\n");
    }

    /** Matériel mTLS d'un membre : présente son certificat, ne croit que cette autorité. */
    public SslBundle bundle(Bicle tls) {
        return bundle(tls, autorite);
    }

    /** Matériel d'un client sans certificat, qui ne croit que cette autorité. */
    public SslBundle bundleSansCertificat() {
        return SslBundle.of(SslStoreBundle.of(null, null, confiance(autorite)));
    }

    /** Matériel mTLS quelconque, pour simuler un client muni d'un certificat étranger. */
    public static SslBundle bundle(Bicle tls, X509Certificate autoriteCrue) {
        try {
            KeyStore cle = KeyStore.getInstance("PKCS12");
            cle.load(null, null);
            cle.setKeyEntry(
                    "tls",
                    tls.clePrivee(),
                    new char[0],
                    tls.chaine().toArray(X509Certificate[]::new));
            return SslBundle.of(SslStoreBundle.of(cle, null, confiance(autoriteCrue)));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public SSLContext contexteTls(Bicle tls) {
        return bundle(tls).createSslContext();
    }

    private static KeyStore confiance(X509Certificate autorite) {
        try {
            KeyStore confiance = KeyStore.getInstance("PKCS12");
            confiance.load(null, null);
            confiance.setCertificateEntry("autorite", autorite);
            return confiance;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Bicle emettre(
            String nomCommun,
            List<GeneralName> noms,
            KeyUsage usage,
            ExtendedKeyUsage usageEtendu,
            Instant instant) {
        KeyPair paire = paire();
        X509v3CertificateBuilder constructeur =
                constructeur(
                        new X500Name("CN=" + nomCommun),
                        paire,
                        instant.minus(VALIDITE),
                        instant.plus(VALIDITE));
        try {
            constructeur.addExtension(
                    Extension.basicConstraints, true, new BasicConstraints(false));
            if (usage != null) {
                constructeur.addExtension(Extension.keyUsage, true, usage);
            }
            constructeur.addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    new GeneralNames(noms.toArray(GeneralName[]::new)));
            if (usageEtendu != null) {
                constructeur.addExtension(Extension.extendedKeyUsage, false, usageEtendu);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Bicle(paire.getPrivate(), List.of(signer(constructeur)));
    }

    private X509v3CertificateBuilder constructeur(
            X500Name sujet, KeyPair paire, Instant debut, Instant fin) {
        return new JcaX509v3CertificateBuilder(
                nom,
                new BigInteger(120, ALEA),
                Date.from(debut),
                Date.from(fin),
                sujet,
                paire.getPublic());
    }

    private X509Certificate signer(X509v3CertificateBuilder constructeur) {
        try {
            return new JcaX509CertificateConverter()
                    .getCertificate(
                            constructeur.build(
                                    new JcaContentSignerBuilder("SHA256withECDSA")
                                            .build(cles.getPrivate())));
        } catch (GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair paire() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
            generateur.initialize(new ECGenParameterSpec("secp256r1"), ALEA);
            return generateur.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
