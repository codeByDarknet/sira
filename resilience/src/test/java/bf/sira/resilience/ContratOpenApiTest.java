package bf.sira.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.openapi.ContratOpenApi;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * La résilience n'a pas de route propre : sa spécification décrit {@code GET /v1/admin/etat},
 * servie par la passerelle. Ici, on vérifie ce qui relève de cette couche ; la route elle-même et
 * ses réponses réelles sont contrôlées par les tests de {@code passerelle/serveur}.
 */
class ContratOpenApiTest {

    private static final ContratOpenApi SPEC = ContratOpenApi.lire("resilience/openapi.yaml");

    @Test
    void laBibliothequeNExposeAucuneRoute() {
        assertThat(ContratOpenApi.routesDesControleurs("bf.sira.resilience")).isEmpty();
        assertThat(SPEC.routes()).containsExactly("GET /v1/admin/etat");
    }

    @Test
    void lesEtatsDuDisjoncteurSontCeuxDuCode() {
        assertThat(SPEC.document().at("/components/schemas/Disjoncteur/enum").valueStream())
                .map(n -> n.asString())
                .containsExactlyElementsOf(
                        Arrays.stream(Disjoncteurs.Etat.values()).map(Enum::name).toList());
    }

    @Test
    void lesExemplesRespectentLeursSchemas() {
        assertThat(SPEC.validerExemples()).isEqualTo(3);
    }
}
