package bf.sira.commun.pki;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.ssl.pem.PemContent;
import org.springframework.util.function.SingletonSupplier;

/**
 * Le matériel cryptographique du membre, lu dans son répertoire : {@code tls.crt/.key}, {@code
 * cachet.crt/.key}, {@code chiffrement.crt/.key} et {@code ca.crt}. Chaque fichier n'est lu qu'à sa
 * première utilisation : un composant qui ne signe pas n'a pas besoin d'un cachet.
 */
public final class Pki {

    private final Path repertoire;
    private final Supplier<X509Certificate> autorite;
    private final Supplier<Bicle> cachet;
    private final Supplier<Bicle> chiffrement;
    private final Supplier<X509Certificate> certificatTls;

    public Pki(Path repertoire) {
        this.repertoire = repertoire;
        this.autorite = SingletonSupplier.of(() -> certificats("ca.crt").getFirst());
        this.cachet = SingletonSupplier.of(() -> bicle("cachet"));
        this.chiffrement = SingletonSupplier.of(() -> bicle("chiffrement"));
        this.certificatTls = SingletonSupplier.of(() -> certificats("tls.crt").getFirst());
    }

    /** L'autorité de certification Sira, seule ancre de confiance. */
    public X509Certificate autorite() {
        return autorite.get();
    }

    public Bicle cachet() {
        return cachet.get();
    }

    public Bicle chiffrement() {
        return chiffrement.get();
    }

    public X509Certificate certificatTls() {
        return certificatTls.get();
    }

    private Bicle bicle(String nom) {
        return new Bicle(lire(nom + ".key").getPrivateKey(), certificats(nom + ".crt"));
    }

    private List<X509Certificate> certificats(String fichier) {
        List<X509Certificate> lus = lire(fichier).getCertificates();
        if (lus == null || lus.isEmpty()) {
            throw new IllegalStateException("aucun certificat dans " + repertoire.resolve(fichier));
        }
        return lus;
    }

    private PemContent lire(String fichier) {
        try {
            return PemContent.load(repertoire.resolve(fichier));
        } catch (IOException e) {
            throw new UncheckedIOException("fichier illisible : " + repertoire.resolve(fichier), e);
        }
    }
}
