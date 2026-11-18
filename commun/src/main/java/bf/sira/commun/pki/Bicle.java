package bf.sira.commun.pki;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/** Une clé privée et sa chaîne de certificats, feuille en premier. */
public record Bicle(PrivateKey clePrivee, List<X509Certificate> chaine) {

    public Bicle {
        if (clePrivee == null || chaine == null || chaine.isEmpty()) {
            throw new IllegalArgumentException("clé privée et certificat obligatoires");
        }
        chaine = List.copyOf(chaine);
    }

    public X509Certificate certificat() {
        return chaine.getFirst();
    }

    /** La clé privée ne doit jamais apparaître dans un journal. */
    @Override
    public String toString() {
        return "Bicle[" + certificat().getSubjectX500Principal().getName() + "]";
    }
}
