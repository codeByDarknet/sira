package bf.sira.passerelle.collecte;

import bf.sira.resilience.ConnecteurInjoignable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lecture de la réponse d'un connecteur, commune à tous les transports (contrat §8). */
final class LectureReponse {

    private final JsonMapper json;
    private final int tailleMax;

    LectureReponse(JsonMapper json, int tailleMax) {
        this.json = json;
        this.tailleMax = tailleMax;
    }

    Transport.Reponse lire(ClientHttpResponse reponse) throws IOException {
        int statut = reponse.getStatusCode().value();
        if (reponse.getStatusCode().is5xxServerError()) {
            throw new ConnecteurInjoignable("le connecteur répond " + statut);
        }
        if (statut != HttpStatus.OK.value()) {
            return new Transport.Reponse(statut, null);
        }
        // Lecture bornée : un connecteur ne peut pas saturer la mémoire de la passerelle.
        byte[] corps = reponse.getBody().readNBytes(tailleMax + 1);
        if (corps.length > tailleMax) {
            throw new ConnecteurInjoignable("réponse du connecteur trop volumineuse");
        }
        try {
            JsonNode attestation =
                    json.readTree(new String(corps, StandardCharsets.UTF_8)).get("attestation");
            if (attestation == null || !attestation.isString()) {
                throw new ConnecteurInjoignable("réponse du connecteur sans attestation");
            }
            return new Transport.Reponse(statut, attestation.asString());
        } catch (JacksonException e) {
            throw new ConnecteurInjoignable("réponse du connecteur illisible", e);
        }
    }
}
