package bf.sira.espacecitoyen;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.test.FauxFournisseur;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/** Les gestes d'un navigateur face au serveur : se connecter, puis écrire avec le jeton CSRF. */
public final class Navigateur {

    public static final String URL_PUBLIQUE = "http://citoyen.test";

    private final MockMvc mvc;
    private final FauxFournisseur fournisseur;

    public Navigateur(MockMvc mvc, FauxFournisseur fournisseur) {
        this.mvc = mvc;
        this.fournisseur = fournisseur;
    }

    /** Déroule la connexion jusqu'au retour du fournisseur, qui doit mener à {@code issue}. */
    public MockHttpSession connecter(String issue) throws Exception {
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

    public MockHttpSession connecter() throws Exception {
        return connecter(URL_PUBLIQUE + "/");
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
