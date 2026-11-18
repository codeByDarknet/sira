package bf.sira.console;

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
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Le serveur de la console face à un fournisseur d'identité et une passerelle simulés, tous deux en
 * mTLS : session, relais strict, contrôle des rôles, et chaque refus attendu.
 */
@SpringBootTest(
        properties = {
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=" + Navigateur.URL_PUBLIQUE,
            "sira.http.taille-corps-max=2KB"
        })
@AutoConfigureMockMvc
class ConsoleTest {

    private static final String HABILITATION = "3f1c2a54-9b7e-4c1d-8a2f-5e6d7c8b9a01";
    private static final String APPROBATION = "/api/habilitations/" + HABILITATION + "/approbation";
    private static final String JEU = "{\"code\":\"etat-civil.majeur\",\"niveau\":\"STATUT\"}";
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
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.CONSOLE);
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
        mvc.perform(get("/api/membres"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    /** Le navigateur ne détient jamais de jeton : celui qu'il présenterait est ignoré. */
    @Test
    void unJetonPresenteParLeNavigateurNOuvreRien() throws Exception {
        String jeton = FOURNISSEUR.jetonAcces(PKI.tls(IdentiteMembre.CONSOLE).certificat());

        mvc.perform(get("/api/membres").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isUnauthorized());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void laSessionExposeLesRolesEtPoseLeJetonCsrf() throws Exception {
        MockHttpSession session = navigateur.connecter("instructeur", "auditeur");

        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("auditeur"))
                .andExpect(jsonPath("$.roles[1]").value("instructeur"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
        assertThat(FOURNISSEUR.derniereDemande())
                .containsEntry("client_id", "console")
                .doesNotContainKey("client_secret");
    }

    @Test
    void chaqueRoleDeLaConsolePeutLire() throws Exception {
        for (String role : List.of("instructeur", "autorite", "auditeur")) {
            PASSERELLE.reinitialiser();
            MockHttpSession session = navigateur.connecter(role);

            mvc.perform(get("/api/habilitations?page=2").session(session))
                    .andExpect(status().isOk());

            assertThat(PASSERELLE.seuleRecue().chemin()).isEqualTo("/v1/admin/habilitations");
        }
    }

    @Test
    void uneLectureEstRelayeeEnMtlsAvecLeJetonDeLAdministrateurEtRienDAutre() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");
        PASSERELLE.repondra(200, "application/json", "{\"elements\":[],\"total\":0}");

        mvc.perform(
                        get("/api/journal?type=CONSULTATION&page=2")
                                .session(session)
                                .header("X-Membre", "urn:sira:socle:passerelle")
                                .header("X-Forwarded-For", "10.0.0.1")
                                .header("Cookie", "autre=valeur"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"elements\":[],\"total\":0}", true))
                .andExpect(cookie().doesNotExist("passerelle"));

        FaussePasserelle.Recue recue = PASSERELLE.seuleRecue();
        assertThat(recue.methode()).isEqualTo("GET");
        assertThat(recue.chemin()).isEqualTo("/v1/admin/journal");
        assertThat(recue.parametres()).isEqualTo("type=CONSULTATION&page=2");
        assertThat(recue.membre()).isEqualTo("urn:sira:socle:console");
        assertThat(recue.enTetes().getFirst("Authorization")).startsWith("Bearer ey");
        assertThat(recue.enTetes().keySet())
                .doesNotContain("X-membre", "X-forwarded-for", "Cookie", "X-xsrf-token");
    }

    /** Un compte du royaume sans rôle de la console : connecté, mais rien ne lui est relayé. */
    @Test
    void sansRoleDeLaConsoleRienNEstRelaye() throws Exception {
        MockHttpSession session = navigateur.connecter();

        mvc.perform(get("/api/membres").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME))
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:acces-refuse"));

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void seulLInstructeurEcrit() throws Exception {
        for (String role : List.of("auditeur", "autorite")) {
            MockHttpSession session = navigateur.connecter(role);
            mvc.perform(ecrireJson(post("/api/jeux"), session, JEU))
                    .andExpect(status().isForbidden());
            mvc.perform(ecrireJson(put("/api/jeux/etat-civil.majeur"), session, JEU))
                    .andExpect(status().isForbidden());
            mvc.perform(navigateur.ecrire(delete("/api/membres/concours"), session))
                    .andExpect(status().isForbidden());
            mvc.perform(ecrireJson(post("/api/jeux/essai"), session, "{\"jeu\":" + JEU + "}"))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            navigateur.ecrire(
                                    post("/api/habilitations/" + HABILITATION + "/soumission"),
                                    session))
                    .andExpect(status().isForbidden());
        }
        assertThat(PASSERELLE.recues()).isEmpty();

        MockHttpSession instructeur = navigateur.connecter("instructeur");
        PASSERELLE.repondra(201, "application/json", JEU);
        mvc.perform(ecrireJson(post("/api/jeux"), instructeur, JEU))
                .andExpect(status().isCreated())
                .andExpect(content().json(JEU, true));

        FaussePasserelle.Recue recue = PASSERELLE.seuleRecue();
        assertThat(recue.methode()).isEqualTo("POST");
        assertThat(recue.chemin()).isEqualTo("/v1/admin/jeux");
        assertThat(recue.corps()).isEqualTo(JEU);
        assertThat(recue.enTetes().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void seuleLAutoriteDecide() throws Exception {
        for (String role : List.of("instructeur", "auditeur")) {
            MockHttpSession session = navigateur.connecter(role);
            for (String decision : List.of("approbation", "rejet", "suspension")) {
                mvc.perform(
                                ecrireJson(
                                        post("/api/habilitations/" + HABILITATION + "/" + decision),
                                        session,
                                        "{\"motif\":\"Dossier incomplet\"}"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.type").value("urn:sira:erreur:acces-refuse"));
            }
        }
        for (String role : List.of("instructeur", "auditeur")) {
            MockHttpSession session = navigateur.connecter(role);
            mvc.perform(
                            ecrireJson(
                                    put("/api/registres/etat-civil/adresse"),
                                    session,
                                    "{\"adresse\":\"https://piege.example\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            ecrireJson(
                                    put("/api/membres/concours/retours"),
                                    session,
                                    "{\"retours_autorises\":[]}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(PASSERELLE.recues()).isEmpty();

        MockHttpSession autorite = navigateur.connecter("autorite");
        mvc.perform(navigateur.ecrire(post(APPROBATION), autorite)).andExpect(status().isOk());

        assertThat(PASSERELLE.seuleRecue().chemin())
                .isEqualTo("/v1/admin/habilitations/" + HABILITATION + "/approbation");
    }

    @Test
    void lAuditeurNeTraitePasUnSignalement() throws Exception {
        String traitement = "/api/signalements/" + HABILITATION + "/traitement";
        String reponse = "{\"reponse\":\"Accès justifié par la démarche.\"}";

        mvc.perform(ecrireJson(post(traitement), navigateur.connecter("auditeur"), reponse))
                .andExpect(status().isForbidden());
        assertThat(PASSERELLE.recues()).isEmpty();

        mvc.perform(ecrireJson(post(traitement), navigateur.connecter("autorite"), reponse))
                .andExpect(status().isOk());
        assertThat(PASSERELLE.recues()).hasSize(1);
    }

    @Test
    void uneEcritureSansJetonCsrfEstRefusee() throws Exception {
        MockHttpSession session = navigateur.connecter("instructeur");

        mvc.perform(
                        post("/api/jeux")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JEU))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME))
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:acces-refuse"));

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void uneRouteHorsListeRepond404SansAtteindreLaPasserelle() throws Exception {
        MockHttpSession session = navigateur.connecter("instructeur", "autorite", "auditeur");

        // Routes du citoyen, méthode non prévue, code difforme, sortie du préfixe.
        mvc.perform(get("/api/demandes").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/resume").session(session)).andExpect(status().isNotFound());
        mvc.perform(navigateur.ecrire(delete("/api/journal"), session))
                .andExpect(status().isNotFound());
        mvc.perform(navigateur.ecrire(post("/api/journal/verification"), session))
                .andExpect(status().isNotFound());
        mvc.perform(get(APPROBATION).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/membres/Code_Difforme").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));
        mvc.perform(get("/api/habilitations/pas-un-identifiant").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/membres/concours/").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/membres/..%2f..%2fcitoyen%2fdemandes").session(session))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/membres/%2e%2e/demandes").session(session))
                .andExpect(status().is4xxClientError());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    /** La passerelle refait son contrôle : son refus est rendu sans retouche. */
    @Test
    void uneErreurDeLaPasserelleEstRendueTelleQuelle() throws Exception {
        MockHttpSession session = navigateur.connecter("autorite");
        String probleme =
                "{\"type\":\"urn:sira:erreur:separation-des-roles\",\"title\":\"Séparation des"
                        + " rôles\",\"status\":403,\"code\":\"SEPARATION_DES_ROLES\"}";
        PASSERELLE.repondra(403, PROBLEME, probleme);

        mvc.perform(navigateur.ecrire(post(APPROBATION), session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(PROBLEME))
                .andExpect(content().json(probleme, true));
    }

    /** Jeton refusé par la passerelle (autre royaume, expiré, révoqué) : la session se ferme. */
    @Test
    void unJetonRefuseParLaPasserelleFermeLaSession() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");
        PASSERELLE.repondra(
                401, PROBLEME, "{\"type\":\"urn:sira:erreur:non-authentifie\",\"status\":401}");

        mvc.perform(get("/api/etat").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEME));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void unJetonExpireEstRenouveleAvantLeRelais() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");
        expirerLeJeton(session);

        mvc.perform(get("/api/etat").session(session)).andExpect(status().isOk());

        assertThat(FOURNISSEUR.derniereDemande())
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "renouvellement")
                .containsEntry("client_id", "console");
        assertThat(PASSERELLE.seuleRecue().enTetes().getFirst("Authorization"))
                .startsWith("Bearer ey");
    }

    @Test
    void lesEntreesHorsBornesSontRefusees() throws Exception {
        MockHttpSession session = navigateur.connecter("instructeur");

        mvc.perform(
                        ecrireJson(
                                post("/api/jeux"),
                                session,
                                "{\"libelle\":\"" + "A".repeat(3000) + "\"}"))
                .andExpect(status().isContentTooLarge());
        mvc.perform(
                        navigateur
                                .ecrire(post("/api/jeux"), session)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("code=etat-civil.majeur"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(
                        navigateur
                                .ecrire(post("/api/jeux"), session)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .content("code=etat-civil.majeur"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/journal").param("service", "s".repeat(201)).session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/journal").param("Page[0]", "1").session(session))
                .andExpect(status().isBadRequest());

        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void unParametreEstReencodeAvantDePartir() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");

        mvc.perform(get("/api/journal").param("service", "a&registre=X #/../").session(session))
                .andExpect(status().isOk());

        assertThat(PASSERELLE.seuleRecue().parametres())
                .isEqualTo("service=a%26registre%3DX+%23%2F..%2F");
    }

    @Test
    void uneReponseInattendueDeLaPasserelleNEstPasTransmise() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");

        PASSERELLE.repondra(200, "text/html", "<script>alert(1)</script>");
        mvc.perform(get("/api/etat").session(session))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(PROBLEME));

        PASSERELLE.repondra(302, null, "");
        mvc.perform(get("/api/etat").session(session)).andExpect(status().isBadGateway());
    }

    @Test
    void lesReponsesPortentLesEnTetesDeSecurite() throws Exception {
        MockHttpSession session = navigateur.connecter("auditeur");

        mvc.perform(get("/api/etat").session(session))
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
        MockHttpSession session = navigateur.connecter("auditeur");

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

    private MockHttpServletRequestBuilder ecrireJson(
            MockHttpServletRequestBuilder requete, MockHttpSession session, String corps)
            throws Exception {
        return navigateur
                .ecrire(requete, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps);
    }

    /** Remplace le jeton d'accès de la session par un jeton échu, sans toucher au reste. */
    private void expirerLeJeton(MockHttpSession session) {
        Authentication administrateur =
                ((SecurityContext)
                                session.getAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY))
                        .getAuthentication();
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setSession(session);
        OAuth2AuthorizedClient actuel =
                jetons.loadAuthorizedClient(ConnexionOidc.ENREGISTREMENT, administrateur, requete);
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
                administrateur,
                requete,
                new MockHttpServletResponse());
    }
}
