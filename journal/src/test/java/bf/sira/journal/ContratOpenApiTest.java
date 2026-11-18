package bf.sira.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.openapi.ContratOpenApi;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * La spécification {@code journal/openapi.yaml} et les contrôleurs du journal ne divergent pas. Les
 * réponses réelles de ces routes sont contrôlées dans les tests de la passerelle, qui les sert.
 */
class ContratOpenApiTest {

    private static final ContratOpenApi SPEC = ContratOpenApi.lire("journal/openapi.yaml");

    @Test
    void lesRoutesDeclareesSontCellesDesControleurs() {
        assertThat(SPEC.routes()).isEqualTo(ContratOpenApi.routesDesControleurs("bf.sira.journal"));
    }

    @Test
    void lesExemplesRespectentLeursSchemas() {
        assertThat(SPEC.validerExemples()).isGreaterThanOrEqualTo(SPEC.routes().size());
    }

    /** Garde-fou : le contrôle des réponses réelles refuse bien ce qui s'écarte du fichier. */
    @Test
    void leControleRefuseUneReponseNonConforme() {
        String chemin = "/v1/citoyen/journal/4c8e2a17-9b3f-4d61-a0e5-7f2c9d1b8e44/signalement";
        byte[] recu =
                "{\"id\":\"4c8e2a17-9b3f-4d61-a0e5-7f2c9d1b8e44\",\"cree_le\":\"2026-11-16T10:03:55Z\"}"
                        .getBytes(StandardCharsets.UTF_8);
        assertThat(SPEC.verifier("POST", chemin, 201, "application/json", recu)).isTrue();
        byte[] sansDate = "{\"id\":\"pas-un-uuid\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SPEC.verifier("POST", chemin, 201, "application/json", sansDate))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("non conforme");
        assertThatThrownBy(() -> SPEC.verifier("POST", chemin, 418, "application/json", recu))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("statut non déclaré");
        assertThat(SPEC.verifier("GET", "/v1/ailleurs", 200, "application/json", recu)).isFalse();
    }

    @Test
    void lesTypesDeLigneSontCeuxDuCode() {
        assertThat(SPEC.document().at("/components/schemas/TypeLigne/enum").valueStream())
                .map(n -> n.asString())
                .containsExactlyElementsOf(
                        Arrays.stream(TypeLigne.values()).map(Enum::name).toList());
        assertThat(
                        SPEC.document()
                                .at("/components/schemas/Ligne/properties/resultat/enum")
                                .valueStream())
                .map(n -> n.asString())
                .containsExactlyElementsOf(
                        Arrays.stream(Resultat.values()).map(Enum::name).toList());
    }
}
