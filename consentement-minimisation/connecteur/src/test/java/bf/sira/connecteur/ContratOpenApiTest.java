package bf.sira.connecteur;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.openapi.ContratOpenApi;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * La route du connecteur est celle que décrit {@code consentement-minimisation/openapi.yaml}, et
 * ses réponses réelles (succès et refus) respectent les schémas. Toutes les réponses reçues par les
 * tests du connecteur passent aussi par ce contrôle (voir {@link ConnecteurDeTest#client}).
 */
class ContratOpenApiTest extends ConnecteurDeTest {

    @Test
    void laSeuleRouteEstCelleDeLaSpecification() {
        assertThat(ContratOpenApi.routesDesControleurs("bf.sira.connecteur"))
                .isEqualTo(Set.of("POST /v1/requetes", "GET /v1/schema"));
        assertThat(SPECIFICATION.routes()).contains("POST /v1/requetes", "GET /v1/schema");
    }

    @Test
    void lesReponsesReellesRespectentLesSchemas() {
        int avant = SPECIFICATION.reponsesVerifiees();

        assertThat(envoyer(requete(ADULTE, majeur())).getStatusCode().value()).isEqualTo(200);
        assertThat(envoyer(requete(INCONNU, majeur())).getStatusCode().value()).isEqualTo(404);
        assertThat(envoyer(requete(EN_PANNE, majeur())).getStatusCode().value()).isEqualTo(503);
        assertThat(envoyer(requete(ADULTE, champs("date_naissance"))).getStatusCode().value())
                .isEqualTo(422);
        assertThat(envoyer(SERVICE, "pas.un.jws").getStatusCode().value()).isEqualTo(403);

        assertThat(SPECIFICATION.reponsesVerifiees() - avant).isEqualTo(5);
    }
}
