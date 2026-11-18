package bf.sira.commun.mtls;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Gestionnaire de confiance d'un client : après les contrôles habituels (chaîne jusqu'à l'autorité
 * Sira, nom du serveur), exige que le serveur porte l'identité attendue dans son SAN URI. La
 * poignée de main échoue sinon : aucun octet de la requête ne part.
 */
final class ServeurAttendu extends X509ExtendedTrustManager {

    private final X509ExtendedTrustManager confiance;
    private final IdentiteMembre attendu;

    private ServeurAttendu(X509ExtendedTrustManager confiance, IdentiteMembre attendu) {
        this.confiance = confiance;
        this.attendu = attendu;
    }

    static TrustManager[] envelopper(TrustManager[] gestionnaires, IdentiteMembre attendu) {
        X509ExtendedTrustManager confiance =
                Arrays.stream(gestionnaires)
                        .filter(X509ExtendedTrustManager.class::isInstance)
                        .map(X509ExtendedTrustManager.class::cast)
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException("aucun gestionnaire de confiance X.509"));
        return new TrustManager[] {new ServeurAttendu(confiance, attendu)};
    }

    private void exiger(X509Certificate[] chaine) throws CertificateException {
        if (chaine == null
                || chaine.length == 0
                || !Certificats.uriSan(chaine[0]).equals(Optional.of(attendu.uri()))) {
            throw new CertificateException("le serveur n'est pas " + attendu);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chaine, String type, SSLEngine moteur)
            throws CertificateException {
        confiance.checkServerTrusted(chaine, type, moteur);
        exiger(chaine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chaine, String type, Socket socket)
            throws CertificateException {
        confiance.checkServerTrusted(chaine, type, socket);
        exiger(chaine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chaine, String type)
            throws CertificateException {
        confiance.checkServerTrusted(chaine, type);
        exiger(chaine);
    }

    // Ce gestionnaire ne sert qu'aux clients : il n'authentifie personne d'autre.

    @Override
    public void checkClientTrusted(X509Certificate[] chaine, String type, SSLEngine moteur)
            throws CertificateException {
        throw new CertificateException("gestionnaire réservé aux connexions sortantes");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chaine, String type, Socket socket)
            throws CertificateException {
        throw new CertificateException("gestionnaire réservé aux connexions sortantes");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chaine, String type)
            throws CertificateException {
        throw new CertificateException("gestionnaire réservé aux connexions sortantes");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return confiance.getAcceptedIssuers();
    }
}
