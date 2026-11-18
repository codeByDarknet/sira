package bf.sira.connecteur.requetes;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Seule route du connecteur (contrat §8). L'accès est réservé à la passerelle. */
@RestController
class RequetesControleur {

    private final TraitementRequetes traitement;

    RequetesControleur(TraitementRequetes traitement) {
        this.traitement = traitement;
    }

    record Reponse(String attestation) {}

    @PostMapping(
            path = "/v1/requetes",
            consumes = "application/jose",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Reponse recevoir(@RequestBody String jws) {
        return new Reponse(traitement.traiter(jws));
    }
}
