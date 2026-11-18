package bf.sira.consentement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.openapi.ContratOpenApi;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * La spécification {@code consentement-minimisation/openapi.yaml} et les routes du citoyen ne
 * divergent pas. La route du connecteur ({@code POST /v1/requetes}, {@code GET /v1/schema}) est
 * comparée dans le module du connecteur ; les réponses réelles sont contrôlées dans les tests de la
 * passerelle et du connecteur.
 */
class ContratOpenApiTest {

    private static final ContratOpenApi SPEC =
            ContratOpenApi.lire("consentement-minimisation/openapi.yaml");

    @Test
    void lesRoutesDuCitoyenSontCellesDuControleur() {
        Set<String> citoyen = new TreeSet<>(SPEC.routes());
        citoyen.remove("POST /v1/requetes");
        citoyen.remove("GET /v1/schema");
        assertThat(citoyen).isEqualTo(ContratOpenApi.routesDesControleurs("bf.sira.consentement"));
    }

    @Test
    void lesExemplesRespectentLeursSchemas() {
        assertThat(SPEC.validerExemples()).isGreaterThanOrEqualTo(SPEC.routes().size());
    }

    @Test
    void lesCanauxSontCeuxDuCode() {
        assertThat(SPEC.document().at("/components/schemas/Canal/enum").valueStream())
                .map(n -> n.asString())
                .containsExactlyElementsOf(Arrays.stream(Canal.values()).map(Enum::name).toList());
    }

    /** Garde-fou : un champ ajouté ou un état inconnu est refusé. */
    @Test
    void leControleRefuseUneReponseNonConforme() {
        byte[] bon = "{\"etat\":\"EN_COURS\",\"retour_url\":null}".getBytes(StandardCharsets.UTF_8);
        String chemin = "/v1/citoyen/demandes/8d2f4b61-3c7e-4a90-b5d1-0e9f6a2c7b14/decision";
        assertThat(SPEC.verifier("POST", chemin, 200, "application/json", bon)).isTrue();
        byte[] mauvais =
                "{\"etat\":\"ACCEPTEE\",\"retour_url\":null,\"citoyen\":\"x\"}"
                        .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SPEC.verifier("POST", chemin, 200, "application/json", mauvais))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("non conforme");
    }
}
