package bf.sira.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.test.FauxFournisseur;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/** Les gestes d'un navigateur face au serveur : se connecter, puis écrire avec le jeton CSRF. */
public final class Navigateur {

    public static final String URL_PUBLIQUE = "http://console.test";

    private final MockMvc mvc;
    private final FauxFournisseur fournisseur;

    public Navigateur(MockMvc mvc, FauxFournisseur fournisseur) {
        this.mvc = mvc;
        this.fournisseur = fournisseur;
    }

    /** Déroule la connexion jusqu'au retour du fournisseur, qui doit mener à {@code issue}. */
    public MockHttpSession ouvrirSession(String issue) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String versFournisseur =
                mvc.perform(get("/api/auth/connexion/sira").session(session))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        var parametres =
                UriComponentsBuilder.fromUriString(versFournisseur).build().getQueryParams();
        fournisseur.nonce(parametres.getFirst("nonce"));
        mvc.perform(
                        get("/api/auth/retour")
                                .param("code", "code-a-usage-unique")
                                .param(
                                        "state",
                                        URLDecoder.decode(
                                                parametres.getFirst("state"),
                                                StandardCharsets.UTF_8))
                                .session(session))
                .andExpect(redirectedUrl(issue));
        return session;
    }

    /**
     * Ouvre une session puis lui donne les rôles voulus. Le fournisseur simulé n'émet que le rôle
     * {@code citoyen} : les rôles de la console sont posés sur la session une fois ouverte, les
     * jetons restant ceux reçus à la connexion.
     */
    public MockHttpSession connecter(String... roles) throws Exception {
        MockHttpSession session = ouvrirSession(URL_PUBLIQUE + "/");
        SecurityContext contexte =
                (SecurityContext)
                        session.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        OAuth2AuthenticationToken connecte =
                (OAuth2AuthenticationToken) contexte.getAuthentication();
        List<GrantedAuthority> autorites =
                Arrays.stream(roles)
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        OidcUser administrateur =
                new DefaultOidcUser(
                        autorites, ((OidcUser) connecte.getPrincipal()).getIdToken(), "sub");
        contexte.setAuthentication(
                new OAuth2AuthenticationToken(
                        administrateur, autorites, connecte.getAuthorizedClientRegistrationId()));
        return session;
    }

    /** Ajoute à une écriture la session et le jeton CSRF, comme le fait l'interface. */
    public MockHttpServletRequestBuilder ecrire(
            MockHttpServletRequestBuilder requete, MockHttpSession session) throws Exception {
        Cookie csrf =
                mvc.perform(get("/api/session").session(session))
                        .andReturn()
                        .getResponse()
                        .getCookie("XSRF-TOKEN");
        return requete.session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }
}
