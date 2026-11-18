package bf.sira.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.oidc.ConnexionOidc;
import bf.sira.commun.openapi.ContratOpenApi;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code guichet-agent/openapi.yaml} face au serveur. Les réponses de tous les tests qui héritent
 * de {@link ServiceDeTest} y sont déjà confrontées ; ce test vérifie en plus les routes, les
 * exemples, et les réponses qu'aucun autre test ne provoque.
 */
class ContratOpenApiTest extends ServiceDeTest {

    private static final String JSON = "application/json";

    private int appels;

    @Test
    void chaqueRouteDuFichierAUneOrigineDansLeCode() {
        Set<String> propres = new TreeSet<>(SPECIFICATION.routesDeSource("demarche"));
        propres.addAll(SPECIFICATION.routesDeSource("parcours"));
        propres.addAll(SPECIFICATION.routesDeSource("agent"));
        assertThat(ContratOpenApi.routesDesControleurs("bf.sira.demo.service")).isEqualTo(propres);

        // La session et la connexion viennent de la bibliothèque commune (dont le jar de test
        // ajoute un contrôleur d'essai, d'où « contains »).
        assertThat(ContratOpenApi.routesDesControleurs("bf.sira.commun.oidc"))
                .contains("GET /api/session");
        assertThat(SPECIFICATION.routesDeSource("commun")).containsExactly("GET /api/session");
        assertThat(SPECIFICATION.routesDeSource("securite"))
                .containsExactlyInAnyOrder(
                        "GET "
                                + ConnexionOidc.CHEMIN_CONNEXION
                                + "/"
                                + ConnexionOidc.ENREGISTREMENT,
                        "GET " + ConnexionOidc.CHEMIN_RETOUR,
                        "POST " + ConnexionOidc.CHEMIN_DECONNEXION);

        Set<String> classees = new TreeSet<>(propres);
        classees.addAll(SPECIFICATION.routesDeSource("commun"));
        classees.addAll(SPECIFICATION.routesDeSource("securite"));
        assertThat(classees).isEqualTo(SPECIFICATION.routes());
    }

    @Test
    void lesExemplesSontConformesAuxSchemas() {
        assertThat(SPECIFICATION.validerExemples()).isGreaterThanOrEqualTo(15);
    }

    /** Les réponses que les autres tests ne provoquent pas ; le contrôle est celui de la base. */
    @Test
    void lesAutresReponsesReellesSontConformes() throws Exception {
        int avant = SPECIFICATION.reponsesVerifiees();

        appeler(get("/api/demarche"), 200);
        appeler(get("/api/session"), 401);
        appeler(get("/api/session").with(agent("concours")), 200);
        appeler(get("/api/auth/connexion/sira?retour=/agent").session(new MockHttpSession()), 302);
        appeler(get("/api/auth/retour?code=x&state=inconnu").session(new MockHttpSession()), 302);
        appeler(post(ConnexionOidc.CHEMIN_DECONNEXION).with(agent("concours")), 403);

        MockHttpSession navigateur = new MockHttpSession();
        appeler(
                post("/api/parcours/demandes")
                        .session(navigateur)
                        .with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("CODE"),
                415);
        appeler(
                post("/api/parcours/demandes")
                        .session(navigateur)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"" + "C".repeat(300 * 1024) + "\"}"),
                413);
        appeler(get("/api/parcours/demande").session(navigateur), 404);
        appeler(get("/api/parcours/dossier/pieces/PAS_UN_JEU").session(navigateur), 400);

        assertThat(SPECIFICATION.reponsesVerifiees() - avant).isEqualTo(appels);
    }

    @Test
    void uneReponseNonConformeEstRefusee() {
        byte[] corps =
                "{\"nom\":\"Agent\",\"roles\":[],\"inconnu\":\"x\"}"
                        .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SPECIFICATION.verifier("GET", "/api/session", 200, JSON, corps))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("non conforme");
    }

    private void appeler(MockHttpServletRequestBuilder requete, int statut) throws Exception {
        mvc.perform(requete).andExpect(status().is(statut));
        appels++;
    }
}
