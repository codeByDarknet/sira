package bf.sira.commun.mtls;

import bf.sira.commun.identite.IdentiteMembre;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Fabrique des clients HTTP sortants en mTLS : TLS 1.3, certificat de transport du membre présenté,
 * seule l'autorité Sira est crue, le nom du serveur est vérifié, aucune redirection n'est suivie.
 * Chaque client nomme le membre qu'il veut joindre : un serveur dont le SAN URI n'est pas cette
 * identité est refusé pendant la poignée de main (contrat §5.2).
 */
public final class ClientsMtls {

    private final SslBundle bundle;
    private final Duration delaiConnexion;
    private final Duration delaiLecture;
    private final Supplier<RestClient.Builder> constructeurs;
    // Une entrée par membre joint : leur nombre est celui des membres du référentiel.
    private final Map<IdentiteMembre, ClientHttpRequestFactory> fabriques =
            new ConcurrentHashMap<>();

    public ClientsMtls(
            SslBundle bundle,
            Duration delaiConnexion,
            Duration delaiLecture,
            Supplier<RestClient.Builder> constructeurs) {
        this.bundle = bundle;
        this.delaiConnexion = delaiConnexion;
        this.delaiLecture = delaiLecture;
        this.constructeurs = constructeurs;
    }

    /** Un constructeur de {@link RestClient} neuf, réglé en mTLS vers le membre {@code serveur}. */
    public RestClient.Builder constructeur(IdentiteMembre serveur) {
        return constructeurs.get().requestFactory(fabrique(serveur));
    }

    public RestClient client(String urlDeBase, IdentiteMembre serveur) {
        return constructeur(serveur).baseUrl(urlDeBase).build();
    }

    /** Pour les API qui demandent encore un {@code RestOperations} (lecture des JWKS). */
    public RestTemplate restTemplate(IdentiteMembre serveur) {
        return new RestTemplate(fabrique(serveur));
    }

    public ClientHttpRequestFactory fabrique(IdentiteMembre serveur) {
        return fabriques.computeIfAbsent(serveur, this::creer);
    }

    private ClientHttpRequestFactory creer(IdentiteMembre serveur) {
        SslManagerBundle gestionnaires = bundle.getManagers();
        SslManagerBundle exigeants =
                new SslManagerBundle() {
                    @Override
                    public KeyManagerFactory getKeyManagerFactory() {
                        return gestionnaires.getKeyManagerFactory();
                    }

                    @Override
                    public TrustManagerFactory getTrustManagerFactory() {
                        return gestionnaires.getTrustManagerFactory();
                    }

                    @Override
                    public TrustManager[] getTrustManagers() {
                        return ServeurAttendu.envelopper(gestionnaires.getTrustManagers(), serveur);
                    }
                };
        SslBundle lie =
                SslBundle.of(
                        bundle.getStores(),
                        bundle.getKey(),
                        bundle.getOptions(),
                        bundle.getProtocol(),
                        exigeants);
        return ClientHttpRequestFactoryBuilder.jdk()
                .build(
                        HttpClientSettings.ofSslBundle(lie)
                                .withRedirects(HttpRedirects.DONT_FOLLOW)
                                .withTimeouts(delaiConnexion, delaiLecture));
    }
}
