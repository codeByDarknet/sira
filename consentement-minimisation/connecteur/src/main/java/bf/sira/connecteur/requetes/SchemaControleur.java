package bf.sira.connecteur.requetes;

import bf.sira.connecteur.politique.Politique;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le schéma du registre : les champs et documents que la politique locale accepte d'exposer, avec
 * leur nature. La console s'en sert pour ne proposer, à la création d'une question, que ce que le
 * registre peut vraiment rendre. Réservé au même appelant que les requêtes (la passerelle).
 */
@RestController
class SchemaControleur {

    private final Politique politique;

    SchemaControleur(Politique politique) {
        this.politique = politique;
    }

    @GetMapping(path = "/v1/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    Politique.Schema schema() {
        return politique.schema();
    }
}
