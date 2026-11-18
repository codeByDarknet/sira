package bf.sira.espacecitoyen.relais;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.oidc.ConnexionOidc;
import bf.sira.commun.openapi.ContratOpenApi;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.espacecitoyen.FaussePasserelle;
import bf.sira.espacecitoyen.Navigateur;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code espace-usager/openapi.yaml} face au serveur : routes servies, liste blanche du relais, et
 * réponses réelles validées contre les schémas. La fausse passerelle répond avec les exemples des
 * spécifications de la passerelle : le test vérifie que le relais les rend intacts et conformes.
 */
@SpringBootTest(
        properties = {
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=" + Navigateur.URL_PUBLIQUE,
            "sira.http.taille-corps-max=2KB"
        })
@AutoConfigureMockMvc
class ContratOpenApiTest {

    private static final ContratOpenApi SPECIFICATION =
            ContratOpenApi.lire("espace-usager/openapi.yaml");
    private static final ContratOpenApi CONSENTEMENT =
            ContratOpenApi.lire("consentement-minimisation/openapi.yaml");
    private static final ContratOpenApi JOURNAL = ContratOpenApi.lire("journal/openapi.yaml");

    private static final String ID = "8d2f4b61-3c7e-4a90-b5d1-0e9f6a2c7b14";
    private static final String JSON = "application/json";
    private static final String PROBLEME = "application/problem+json";

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur FOURNISSEUR = new FauxFournisseur(PKI);
    private static final FaussePasserelle PASSERELLE = new FaussePasserelle(PKI);
    private static final Path CLES = cles();

    @Autowired private MockMvc mvc;

    private Navigateur navigateur;

    private static Path cles() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.ESPACE_CITOYEN);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", CLES::toString);
        registre.add("sira.oidc.url-interne", FOURNISSEUR::urlInterne);
        registre.add("sira.passerelle.url", PASSERELLE::url);
    }

    @BeforeEach
    void preparer() {
        PASSERELLE.reinitialiser();
        navigateur = new Navigateur(mvc, FOURNISSEUR);
    }

    @AfterAll
    static void arreter() {
        PASSERELLE.close();
        FOURNISSEUR.close();
    }

    @Test
    void chaqueRouteDuFichierAUneOrigineDansLeCode() {
        // Un seul contrôleur ici, le relais ; la session vient de la bibliothèque commune (dont
        // le jar de test ajoute un contrôleur d'essai, d'où « contains »).
        assertThat(ContratOpenApi.routesDesControleurs("bf.sira.espacecitoyen"))
                .containsExactly("TOUTES /api/**");
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
        Set<String> classees = new TreeSet<>(SPECIFICATION.routesDeSource("relais"));
        classees.addAll(SPECIFICATION.routesDeSource("commun"));
        classees.addAll(SPECIFICATION.routesDeSource("securite"));
        assertThat(classees).isEqualTo(SPECIFICATION.routes());
    }

    /**
     * Chaque route relayée du fichier : une seule route de la liste blanche, servie par la
     * passerelle.
     */
    @Test
    @SuppressWarnings("unchecked")
    void laListeBlancheEstCelleDuFichier() {
        List<Route> blanche =
                (List<Route>) ReflectionTestUtils.getField(RoutesCitoyen.class, "ROUTES");
        Set<String> relayees = SPECIFICATION.routesDeSource("relais");
        Set<String> passerelle = new TreeSet<>(CONSENTEMENT.routes());
        passerelle.addAll(JOURNAL.routes());

        assertThat(relayees).hasSameSizeAs(blanche);
        for (String route : relayees) {
            String[] parties = route.split(" ", 2);
            String suite = parties[1].substring("/api".length());
            HttpMethod methode = HttpMethod.valueOf(parties[0]);
            assertThat(blanche.stream().filter(r -> r.accepte(methode, suite.replace("{id}", ID))))
                    .as(route)
                    .hasSize(1);
            assertThat(passerelle).contains(parties[0] + " /v1/citoyen" + suite);
        }
    }

    @Test
    void lesExemplesSontConformesAuxSchemas() {
        assertThat(SPECIFICATION.validerExemples()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void lesReponsesReellesSontConformes() throws Exception {
        int avant = SPECIFICATION.reponsesVerifiees();

        // Session et connexion.
        appeler(get("/api/session"), 401);
        appeler(
                get("/api/auth/connexion/sira?retour=/demandes").session(new MockHttpSession()),
                302);
        appeler(get("/api/auth/retour?code=x&state=inconnu").session(new MockHttpSession()), 302);
        MockHttpSession session = navigateur.connecter();
        appeler(get("/api/session").session(session), 200);

        // Réponses de la passerelle relayées.
        PASSERELLE.repondra(
                200,
                JSON,
                exemple(CONSENTEMENT, "/paths/~1v1~1citoyen~1resume/get/responses/200", JSON));
        appeler(get("/api/resume").session(session), 200);
        PASSERELLE.repondra(
                200,
                JSON,
                exemple(CONSENTEMENT, "/paths/~1v1~1citoyen~1demandes/get/responses/200", JSON));
        appeler(get("/api/demandes?etat=EN_ATTENTE_ACCORD").session(session), 200);
        PASSERELLE.repondra(
                200, JSON, exemple(CONSENTEMENT, "/components/responses/Demande", JSON));
        appeler(get("/api/demandes/" + ID).session(session), 200);
        PASSERELLE.repondra(
                404,
                PROBLEME,
                exemple(CONSENTEMENT, "/components/responses/DemandeIntrouvable", PROBLEME));
        appeler(get("/api/demandes/" + ID).session(session), 404);
        String decision = "/api/demandes/" + ID + "/decision";
        PASSERELLE.repondra(
                422,
                PROBLEME,
                exemple(
                        CONSENTEMENT,
                        "/paths/~1v1~1citoyen~1demandes~1{id}~1decision/post/responses/422",
                        PROBLEME));
        appeler(
                ecrireJson(
                        post(decision),
                        session,
                        "{\"decision\":\"ACCEPTER\",\"code_verification\":\"1\"}"),
                422);
        PASSERELLE.repondra(200, JSON, "{\"etat\":\"EN_COURS\",\"retour_url\":null}");
        appeler(
                ecrireJson(
                        post(decision),
                        session,
                        "{\"decision\":\"ACCEPTER\",\"code_verification\":\"492113\"}"),
                200);
        PASSERELLE.repondra(204, null, "");
        appeler(navigateur.ecrire(delete("/api/autorisations/" + ID), session), 204);
        PASSERELLE.repondra(
                200,
                JSON,
                exemple(JOURNAL, "/paths/~1v1~1citoyen~1journal/get/responses/200", JSON));
        appeler(get("/api/journal").session(session), 200);

        // Refus du serveur lui-même, sans atteindre la passerelle.
        PASSERELLE.reinitialiser();
        appeler(get("/api/demandes/pas-un-identifiant").session(session), 404);
        appeler(
                post(decision)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                403);
        appeler(
                navigateur
                        .ecrire(post(decision), session)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"),
                415);
        appeler(
                ecrireJson(post(decision), session, "{\"decision\":\"" + "A".repeat(3000) + "\"}"),
                413);
        appeler(get("/api/journal").param("service", "s".repeat(201)).session(session), 400);
        assertThat(PASSERELLE.recues()).isEmpty();
        PASSERELLE.repondra(200, "text/html", "<p>page</p>");
        appeler(get("/api/resume").session(session), 502);

        appeler(navigateur.ecrire(post(ConnexionOidc.CHEMIN_DECONNEXION), session), 200);

        assertThat(SPECIFICATION.reponsesVerifiees() - avant).isEqualTo(19);
    }

    @Test
    void uneReponseNonConformeEstRefusee() {
        byte[] corps = "{\"nom\":\"Awa Ouédraogo\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SPECIFICATION.verifier("GET", "/api/session", 200, JSON, corps))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("non conforme");
    }

    private void appeler(MockHttpServletRequestBuilder requete, int statut) throws Exception {
        mvc.perform(requete)
                .andDo(ContratOpenApi.controle(SPECIFICATION))
                .andExpect(status().is(statut));
    }

    private MockHttpServletRequestBuilder ecrireJson(
            MockHttpServletRequestBuilder requete, MockHttpSession session, String corps)
            throws Exception {
        return navigateur
                .ecrire(requete, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps);
    }

    /** L'exemple d'une réponse de la passerelle, tel que sa spécification le donne. */
    private static String exemple(ContratOpenApi specification, String reponse, String type) {
        String pointeur = reponse + "/content/" + type.replace("/", "~1") + "/example";
        var noeud = specification.document().at(pointeur);
        assertThat(noeud.isMissingNode()).as(pointeur).isFalse();
        return noeud.toString();
    }
}
