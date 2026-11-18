package bf.sira.passerelle.collecte;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.passerelle.config.Proprietes;
import bf.sira.resilience.ConnecteurInjoignable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/** Transport par défaut : la passerelle appelle le connecteur en mTLS, sans intermédiaire. */
@Component
class TransportDirect implements Transport {

    static final MediaType JOSE = MediaType.parseMediaType("application/jose");

    private final ClientsMtls clients;
    private final LectureReponse lecture;
    // Un client par registre et adresse : chacun n'accepte que le connecteur de son registre.
    private final Map<List<String>, RestClient> parRegistre = new ConcurrentHashMap<>();

    TransportDirect(ClientsMtls clients, JsonMapper json, Proprietes proprietes) {
        this.clients = clients;
        this.lecture =
                new LectureReponse(
                        json,
                        Math.toIntExact(proprietes.collecte().tailleAttestationMax().toBytes()));
    }

    @Override
    public String code() {
        return "DIRECT";
    }

    @Override
    public Reponse envoyer(String registre, String adresse, String requeteSignee) {
        try {
            return parRegistre
                    .computeIfAbsent(
                            List.of(registre, adresse),
                            cle -> clients.client(adresse, IdentiteMembre.registre(registre)))
                    .post()
                    .uri("/v1/requetes")
                    .contentType(JOSE)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requeteSignee)
                    .exchange((requete, reponse) -> lecture.lire(reponse));
        } catch (RestClientException e) {
            throw new ConnecteurInjoignable("connecteur injoignable", e);
        }
    }

    @Override
    public String schema(String registre, String adresse) {
        try {
            return parRegistre
                    .computeIfAbsent(
                            List.of(registre, adresse),
                            cle -> clients.client(adresse, IdentiteMembre.registre(registre)))
                    .get()
                    .uri("/v1/schema")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ConnecteurInjoignable("connecteur injoignable", e);
        }
    }
}
