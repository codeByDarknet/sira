package bf.sira.commun.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Le parcours de connexion d'un serveur d'application en mode {@code client}, face à un fournisseur
 * d'identité simulé dont le point de jetons exige le mTLS.
 */
@SpringBootTest(
        properties = {
            "sira.mtls.mode=client",
            "sira.oidc.client-id=sira-espace-citoyen",
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=http://citoyen.test/"
        })
@AutoConfigureMockMvc
class ConnexionOidcTest {

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur FOURNISSEUR = new FauxFournisseur(PKI);
    private static final FauxFournisseur PASSERELLE =
            new FauxFournisseur(PKI, IdentiteMembre.PASSERELLE);
    private static final Path REPERTOIRE = repertoire();

    @Autowired private MockMvc mvc;

    private static Path repertoire() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.ESPACE_CITOYEN);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", REPERTOIRE::toString);
        registre.add("sira.oidc.url-interne", FOURNISSEUR::urlInterne);
        registre.add("sira.passerelle.url", PASSERELLE::racine);
    }

    @AfterAll
    static void arreter() {
        FOURNISSEUR.close();
        PASSERELLE.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Routes.class)
    static class Appli {

        @Bean
        SecurityFilterChain securite(HttpSecurity http, ConnexionOidc connexion) throws Exception {
            connexion.appliquer(http);
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
            return http.build();
        }
    }

    @RestController
    static class Routes {

        private final RelaisPasserelle passerelle;

        Routes(RelaisPasserelle passerelle) {
            this.passerelle = passerelle;
        }

        @GetMapping("/api/relais")
        Map<?, ?> relais() {
            return passerelle.client().get().uri("/v1/echo").retrieve().body(Map.class);
        }
    }

    /** Déroule la connexion jusqu'au retour du fournisseur et rend la session ouverte. */
    private MockHttpSession connecter(String depart, String retourAttendu) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult versFournisseur =
                mvc.perform(get(depart).session(session))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
        var autorisation =
                UriComponentsBuilder.fromUriString(versFournisseur.getResponse().getRedirectedUrl())
                        .build();
        var parametres = autorisation.getQueryParams();

        assertThat(autorisation.getHost()).isEqualTo("identite.test");
        assertThat(autorisation.getPath()).isEqualTo("/realms/test/protocol/openid-connect/auth");
        assertThat(parametres.getFirst("response_type")).isEqualTo("code");
        assertThat(parametres.getFirst("client_id")).isEqualTo("sira-espace-citoyen");
        assertThat(parametres.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(parametres.getFirst("code_challenge")).isNotBlank();
        assertThat(parametres.getFirst("redirect_uri"))
                .isEqualTo("http://citoyen.test/api/auth/retour");

        FOURNISSEUR.nonce(parametres.getFirst("nonce"));
        mvc.perform(
                        get("/api/auth/retour")
                                .param("code", "code-a-usage-unique")
                                .param("state", decoder(parametres.getFirst("state")))
                                .session(session))
                .andExpect(redirectedUrl(retourAttendu));
        return session;
    }

    private static String decoder(String valeur) {
        return URLDecoder.decode(valeur, StandardCharsets.UTF_8);
    }

    @Test
    void sansSessionLApiRepond401EnJson() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:non-authentifie"));
        mvc.perform(get("/api/relais")).andExpect(status().isUnauthorized());
    }

    @Test
    void laConnexionEchangeLeCodeEnMtlsSansSecret() throws Exception {
        MockHttpSession session =
                connecter(
                        "/api/auth/connexion/sira?retour=/demandes/42",
                        "http://citoyen.test/demandes/42");

        Map<String, String> demande = FOURNISSEUR.derniereDemande();
        assertThat(demande)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "code-a-usage-unique")
                .containsEntry("client_id", "sira-espace-citoyen")
                .containsEntry("redirect_uri", "http://citoyen.test/api/auth/retour")
                .containsKey("code_verifier")
                .doesNotContainKey("client_secret");
        assertThat(FOURNISSEUR.dernierEnTeteAuthorization()).isNull();
        assertThat(Certificats.uriSan(FOURNISSEUR.dernierCertificat()))
                .contains("urn:sira:socle:espace-citoyen");

        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Awa Ouédraogo"))
                .andExpect(jsonPath("$.roles[0]").value("citoyen"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void unRetourVersUnAutreSiteEstIgnore() throws Exception {
        connecter(
                "/api/auth/connexion/sira?retour=https://pirate.example/", "http://citoyen.test/");

        assertThat(ConnexionOidc.estCheminLocal("/demandes/42?onglet=pieces")).isTrue();
        assertThat(ConnexionOidc.estCheminLocal("//pirate.example")).isFalse();
        assertThat(ConnexionOidc.estCheminLocal("/\\pirate.example")).isFalse();
        assertThat(ConnexionOidc.estCheminLocal("demandes")).isFalse();
        assertThat(ConnexionOidc.estCheminLocal("/a\r\nSet-Cookie: x")).isFalse();
        assertThat(ConnexionOidc.estCheminLocal(null)).isFalse();
    }

    @Test
    void seulesLesActionsSurSonCompteConnuesPassentAuFournisseur() throws Exception {
        assertThat(ConnexionOidc.actionCompte("mot-de-passe")).isEqualTo("UPDATE_PASSWORD");
        assertThat(ConnexionOidc.actionCompte("authentification")).isEqualTo("CONFIGURE_TOTP");
        assertThat(ConnexionOidc.actionCompte("delete_account")).isNull();
        assertThat(ConnexionOidc.actionCompte("UPDATE_PASSWORD")).isNull();
        assertThat(ConnexionOidc.actionCompte(null)).isNull();

        String vers =
                mvc.perform(get("/api/auth/connexion/sira?action=mot-de-passe"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(vers).contains("kc_action=UPDATE_PASSWORD");

        String ordinaire =
                mvc.perform(get("/api/auth/connexion/sira?action=delete_account"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(ordinaire).doesNotContain("kc_action");
    }

    @Test
    void leRelaisPresenteLeJetonDeLUtilisateurEnMtls() throws Exception {
        MockHttpSession session = connecter("/api/auth/connexion/sira", "http://citoyen.test/");

        mvc.perform(get("/api/relais").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membre").value("urn:sira:socle:espace-citoyen"));

        assertThat(PASSERELLE.dernierEnTeteAuthorization()).startsWith("Bearer ey");
    }

    @Test
    void unEtatInconnuNOuvrePasDeSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(
                        get("/api/auth/retour")
                                .param("code", "code-vole")
                                .param("state", "etat-forge")
                                .session(session))
                .andExpect(redirectedUrl("http://citoyen.test/?erreur=connexion"));
        mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void laDeconnexionExigeLeJetonCsrfEtRendLAdresseDeFinDeSession() throws Exception {
        MockHttpSession session = connecter("/api/auth/connexion/sira", "http://citoyen.test/");

        mvc.perform(post("/api/auth/deconnexion").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:acces-refuse"));

        Cookie csrf =
                mvc.perform(get("/api/session").session(session))
                        .andReturn()
                        .getResponse()
                        .getCookie("XSRF-TOKEN");
        MvcResult deconnexion =
                mvc.perform(
                                post("/api/auth/deconnexion")
                                        .session(session)
                                        .cookie(csrf)
                                        .header("X-XSRF-TOKEN", csrf.getValue()))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("application/json"))
                        .andReturn();
        String url = JsonPath.read(deconnexion.getResponse().getContentAsString(), "$.url");
        assertThat(url)
                .startsWith("http://identite.test/realms/test/protocol/openid-connect/logout?")
                .contains("id_token_hint=")
                .contains("post_logout_redirect_uri=http://citoyen.test/");
        assertThat(session.isInvalid()).isTrue();
    }
}
