package bf.sira.espacecitoyen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.oidc.ConnexionOidc;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Le serveur de l'espace citoyen face à un fournisseur d'identité et une passerelle simulés, tous
 * deux en mTLS : session, relais strict, et chaque refus attendu.
 */
@SpringBootTest(
        properties = {
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=" + Navigateur.URL_PUBLIQUE,
            "sira.http.taille-corps-max=2KB"
        })
@AutoConfigureMockMvc
class EspaceCitoyenTest {

    private static final String DEMANDE = "3f1c2a54-9b7e-4c1d-8a2f-5e6d7c8b9a01";
    private static final String PROBLEME = "application/problem+json";

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur FOURNISSEUR = new FauxFournisseur(PKI);
    private static final FaussePasserelle PASSERELLE = new FaussePasserelle(PKI);
    private static final Path CLES = cles();

    @Autowired private MockMvc mvc;
    @Autowired private OAuth2AuthorizedClientRepository jetons;

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
    void sansSessionToutRepond401EnJson() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME))
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:non-authentifie"));
        mvc.perform(get("/api/demandes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    /** Le navigateur ne détient jamais de jeton : celui qu'il présenterait est ignoré. */
    @Test
    void unJetonPresenteParLeNavigateurNOuvreRien() throws Exception {
        String jeton = FOURNISSEUR.jetonAcces(PKI.tls(IdentiteMembre.ESPACE_CITOYEN).certificat());

        mvc.perform(get("/api/demandes").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isUnauthorized());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void laSessionDitQuiEstConnecteEtPoseLeJetonCsrf() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Awa Ouédraogo"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
        assertThat(FOURNISSEUR.derniereDemande())
                .containsEntry("client_id", "espace-citoyen")
                .doesNotContainKey("client_secret");
    }

    @Test
    void uneLectureEstRelayeeEnMtlsAvecLeJetonDuCitoyenEtRienDAutre() throws Exception {
        MockHttpSession session = navigateur.connecter();
        PASSERELLE.repondra(200, "application/json", "{\"elements\":[],\"total\":0}");

        mvc.perform(
                        get("/api/demandes?etat=EN_ATTENTE_ACCORD&page=2")
                                .session(session)
                                .header("X-Membre", "urn:sira:socle:console")
                                .header("X-Forwarded-For", "10.0.0.1")
                                .header("Cookie", "autre=valeur"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"elements\":[],\"total\":0}", true))
                .andExpect(cookie().doesNotExist("passerelle"));

        FaussePasserelle.Recue recue = PASSERELLE.seuleRecue();
        assertThat(recue.methode()).isEqualTo("GET");
        assertThat(recue.chemin()).isEqualTo("/v1/citoyen/demandes");
        assertThat(recue.parametres()).isEqualTo("etat=EN_ATTENTE_ACCORD&page=2");
        assertThat(recue.membre()).isEqualTo("urn:sira:socle:espace-citoyen");
        assertThat(recue.enTetes().getFirst("Authorization")).startsWith("Bearer ey");
        assertThat(recue.enTetes().keySet())
                .doesNotContain("X-membre", "X-forwarded-for", "Cookie", "X-xsrf-token");
    }

    @Test
    void uneDecisionEstRelayeeAvecSonCorps() throws Exception {
        MockHttpSession session = navigateur.connecter();
        PASSERELLE.repondra(200, "application/json", "{\"etat\":\"EN_COURS\",\"retour_url\":null}");

        mvc.perform(
                        navigateur
                                .ecrire(post("/api/demandes/" + DEMANDE + "/decision"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"ACCEPTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_COURS"));

        FaussePasserelle.Recue recue = PASSERELLE.seuleRecue();
        assertThat(recue.methode()).isEqualTo("POST");
        assertThat(recue.chemin()).isEqualTo("/v1/citoyen/demandes/" + DEMANDE + "/decision");
        assertThat(recue.corps()).isEqualTo("{\"decision\":\"ACCEPTER\"}");
        assertThat(recue.enTetes().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void unRetraitSansContenuEstRelaye() throws Exception {
        MockHttpSession session = navigateur.connecter();
        PASSERELLE.repondra(204, null, "");

        mvc.perform(navigateur.ecrire(delete("/api/autorisations/" + DEMANDE), session))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(PASSERELLE.seuleRecue().methode()).isEqualTo("DELETE");
    }

    @Test
    void uneEcritureSansJetonCsrfEstRefusee() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(
                        post("/api/demandes/" + DEMANDE + "/decision")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"ACCEPTER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME))
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:acces-refuse"));

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void uneRouteHorsListeRepond404SansAtteindreLaPasserelle() throws Exception {
        MockHttpSession session = navigateur.connecter();

        // Routes d'administration, méthode non prévue, identifiant difforme, sortie du préfixe.
        mvc.perform(get("/api/membres").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/demandes/" + DEMANDE + "/decision").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(navigateur.ecrire(put("/api/demandes/" + DEMANDE), session))
                .andExpect(status().isNotFound());
        mvc.perform(navigateur.ecrire(delete("/api/journal"), session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/demandes/pas-un-identifiant").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));
        mvc.perform(get("/api/demandes/" + DEMANDE + "/").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/demandes/..%2f..%2fadmin%2fmembres").session(session))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/demandes/%2e%2e/admin").session(session))
                .andExpect(status().is4xxClientError());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    /** Une demande d'un autre citoyen : la passerelle répond 404, rendu sans retouche. */
    @Test
    void uneErreurDeLaPasserelleEstRendueTelleQuelle() throws Exception {
        MockHttpSession session = navigateur.connecter();
        String probleme =
                "{\"type\":\"urn:sira:erreur:introuvable\",\"title\":\"Ressource introuvable\","
                        + "\"status\":404,\"code\":\"DEMANDE_INTROUVABLE\"}";
        PASSERELLE.repondra(404, PROBLEME, probleme);

        mvc.perform(get("/api/demandes/" + DEMANDE).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEME))
                .andExpect(content().json(probleme, true));
    }

    /** Jeton refusé par la passerelle (autre royaume, expiré, révoqué) : la session se ferme. */
    @Test
    void unJetonRefuseParLaPasserelleFermeLaSession() throws Exception {
        MockHttpSession session = navigateur.connecter();
        PASSERELLE.repondra(
                401, PROBLEME, "{\"type\":\"urn:sira:erreur:non-authentifie\",\"status\":401}");

        mvc.perform(get("/api/resume").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEME));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unJetonExpireEstRenouveleAvantLeRelais() throws Exception {
        MockHttpSession session = navigateur.connecter();
        expirerLeJeton(session);

        mvc.perform(get("/api/resume").session(session)).andExpect(status().isOk());

        assertThat(FOURNISSEUR.derniereDemande())
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "renouvellement")
                .containsEntry("client_id", "espace-citoyen");
        assertThat(PASSERELLE.seuleRecue().enTetes().getFirst("Authorization"))
                .startsWith("Bearer ey");
    }

    @Test
    void lesEntreesHorsBornesSontRefusees() throws Exception {
        MockHttpSession session = navigateur.connecter();
        String decision = "/api/demandes/" + DEMANDE + "/decision";

        mvc.perform(
                        navigateur
                                .ecrire(post(decision), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"" + "A".repeat(3000) + "\"}"))
                .andExpect(status().isContentTooLarge());
        mvc.perform(
                        navigateur
                                .ecrire(post(decision), session)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("decision=ACCEPTER"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(
                        navigateur
                                .ecrire(post(decision), session)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .content("decision=ACCEPTER"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/journal").param("service", "s".repeat(201)).session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/journal").param("Page[0]", "1").session(session))
                .andExpect(status().isBadRequest());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void unParametreEstReencodeAvantDePartir() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(get("/api/journal").param("service", "a&etat=X #/../").session(session))
                .andExpect(status().isOk());

        assertThat(PASSERELLE.seuleRecue().parametres())
                .isEqualTo("service=a%26etat%3DX+%23%2F..%2F");
    }

    @Test
    void uneReponseInattendueDeLaPasserelleNEstPasTransmise() throws Exception {
        MockHttpSession session = navigateur.connecter();

        PASSERELLE.repondra(200, "text/html", "<script>alert(1)</script>");
        mvc.perform(get("/api/resume").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));

        PASSERELLE.repondra(302, null, "");
        mvc.perform(get("/api/resume").session(session)).andExpect(status().isBadGateway());
    }

    @Test
    void lesReponsesPortentLesEnTetesDeSecurite() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(get("/api/resume").session(session))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void laDeconnexionFermeLaSessionEtRendLAdresseDuFournisseur() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(navigateur.ecrire(post("/api/auth/deconnexion"), session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.url")
                                .value(
                                        startsWith(
                                                FauxFournisseur.EMETTEUR
                                                        + "/protocol/openid-connect/logout?")));

        assertThat(session.isInvalid()).isTrue();
    }

    /** Remplace le jeton d'accès de la session par un jeton échu, sans toucher au reste. */
    private void expirerLeJeton(MockHttpSession session) {
        Authentication citoyen =
                ((SecurityContext)
                                session.getAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY))
                        .getAuthentication();
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setSession(session);
        OAuth2AuthorizedClient actuel =
                jetons.loadAuthorizedClient(ConnexionOidc.ENREGISTREMENT, citoyen, requete);
        Instant hier = Instant.now().minusSeconds(86_400);
        OAuth2AccessToken echu =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "jeton-echu",
                        hier,
                        hier.plusSeconds(300));
        jetons.saveAuthorizedClient(
                new OAuth2AuthorizedClient(
                        actuel.getClientRegistration(),
                        actuel.getPrincipalName(),
                        echu,
                        actuel.getRefreshToken()),
                citoyen,
                requete,
                new MockHttpServletResponse());
    }
}
