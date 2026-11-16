package bf.sira.commun.oidc;

import bf.sira.commun.erreurs.Problemes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Connexion OIDC avec session, pour un serveur d'application placé derrière le nginx de son
 * interface (qui ne relaie que {@code /api/}). Le navigateur ne reçoit qu'un cookie de session ;
 * les jetons restent côté serveur.
 *
 * <ul>
 *   <li>{@code GET /api/auth/connexion/sira[?retour=/chemin]} : part vers le fournisseur d'identité
 *       (code d'autorisation + PKCE) ;
 *   <li>{@code GET /api/auth/retour} : retour du fournisseur ;
 *   <li>{@code POST /api/auth/deconnexion} : ferme la session et répond {@code {"url": …}},
 *       l'adresse de fin de session du fournisseur que le navigateur doit suivre ;
 *   <li>toute route protégée appelée sans session répond 401 en JSON, jamais une redirection ;
 *   <li>CSRF : le cookie {@code XSRF-TOKEN} est à renvoyer dans l'en-tête {@code X-XSRF-TOKEN}.
 * </ul>
 */
public final class ConnexionOidc {

    /** Identifiant unique de l'enregistrement client : un serveur n'a qu'un fournisseur. */
    public static final String ENREGISTREMENT = "sira";

    public static final String CHEMIN_CONNEXION = "/api/auth/connexion";
    public static final String CHEMIN_RETOUR = "/api/auth/retour";
    public static final String CHEMIN_DECONNEXION = "/api/auth/deconnexion";

    private static final String ATTRIBUT_RETOUR = ConnexionOidc.class.getName() + ".retour";
    private static final int TAILLE_RETOUR_MAX = 512;

    private final ProprietesOidc proprietes;
    private final ClientRegistrationRepository clients;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> clientJetons;
    private final OAuth2UserService<OidcUserRequest, OidcUser> serviceUtilisateur;

    ConnexionOidc(
            ProprietesOidc proprietes,
            ClientRegistrationRepository clients,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> clientJetons,
            OAuth2UserService<OidcUserRequest, OidcUser> serviceUtilisateur) {
        this.proprietes = proprietes;
        this.clients = clients;
        this.clientJetons = clientJetons;
        this.serviceUtilisateur = serviceUtilisateur;
    }

    /** Applique la connexion à une chaîne de filtres ; les règles par route restent à déclarer. */
    public void appliquer(HttpSecurity http) throws Exception {
        http.oauth2Login(connexion ->
                        connexion
                                .authorizationEndpoint(
                                        a -> a.authorizationRequestResolver(resolveur()))
                                .redirectionEndpoint(r -> r.baseUri(CHEMIN_RETOUR))
                                .tokenEndpoint(t -> t.accessTokenResponseClient(clientJetons))
                                .userInfoEndpoint(u -> u.oidcUserService(serviceUtilisateur))
                                .successHandler(apresConnexion())
                                .failureUrl(proprietes.urlPublique() + "/?erreur=connexion"))
                .logout(deconnexion ->
                        deconnexion
                                .logoutUrl(CHEMIN_DECONNEXION)
                                .logoutSuccessHandler(new ApresDeconnexion()))
                .csrf(CsrfConfigurer::spa)
                .addFilterAfter(new CookieCsrf(), CsrfFilter.class)
                .exceptionHandling(e ->
                        e.authenticationEntryPoint(Problemes.nonAuthentifie())
                                .accessDeniedHandler(Problemes.accesRefuse()));
    }

    /** Mémorise, au départ vers le fournisseur, la page de l'interface où revenir ensuite. */
    private OAuth2AuthorizationRequestResolver resolveur() {
        OAuth2AuthorizationRequestResolver delegue =
                new DefaultOAuth2AuthorizationRequestResolver(clients, CHEMIN_CONNEXION);
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest requete) {
                return memoriser(requete, delegue.resolve(requete));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest requete, String client) {
                return memoriser(requete, delegue.resolve(requete, client));
            }
        };
    }

    private static OAuth2AuthorizationRequest memoriser(
            HttpServletRequest requete, OAuth2AuthorizationRequest demande) {
        if (demande == null) {
            return null;
        }
        String retour = requete.getParameter("retour");
        if (estCheminLocal(retour)) {
            requete.getSession().setAttribute(ATTRIBUT_RETOUR, retour);
        }
        String action = actionCompte(requete.getParameter("action"));
        if (action == null) {
            return demande;
        }
        // Action du fournisseur d'identité lancée par l'application (« kc_action ») : la personne,
        // déjà connectée, change son mot de passe ou son application d'authentification, puis
        // revient.
        return OAuth2AuthorizationRequest.from(demande)
                .additionalParameters(parametres -> parametres.put("kc_action", action))
                .build();
    }

    /**
     * Actions sur son propre compte qu'une interface peut demander : seules celles-ci passent,
     * toute autre valeur est ignorée (connexion ordinaire).
     */
    static String actionCompte(String action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case "mot-de-passe" -> "UPDATE_PASSWORD";
            case "authentification" -> "CONFIGURE_TOTP";
            default -> null;
        };
    }

    /**
     * Seul un chemin de l'application elle-même est accepté : jamais une adresse complète, qui
     * ferait de la connexion un tremplin vers un site tiers.
     */
    static boolean estCheminLocal(String chemin) {
        return chemin != null
                && chemin.length() <= TAILLE_RETOUR_MAX
                && chemin.startsWith("/")
                && !chemin.startsWith("//")
                && chemin.chars().noneMatch(c -> c == '\\' || c < 0x20 || c == 0x7f);
    }

    private AuthenticationSuccessHandler apresConnexion() {
        return (requete, reponse, authentification) -> {
            HttpSession session = requete.getSession(false);
            Object retour = session != null ? session.getAttribute(ATTRIBUT_RETOUR) : null;
            if (session != null) {
                session.removeAttribute(ATTRIBUT_RETOUR);
            }
            String chemin = retour instanceof String s && estCheminLocal(s) ? s : "/";
            reponse.sendRedirect(proprietes.urlPublique() + chemin);
        };
    }

    /**
     * Le jeton CSRF n'est émis que s'il est lu. Le lire à chaque requête pose le cookie dès le
     * premier appel de l'interface, qui peut alors le renvoyer en en-tête.
     */
    private static final class CookieCsrf extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
                throws ServletException, IOException {
            if (requete.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken jeton) {
                jeton.getToken();
            }
            chaine.doFilter(requete, reponse);
        }
    }

    /**
     * La déconnexion est appelée par l'interface en {@code fetch} : au lieu d'une redirection vers
     * une autre origine, la réponse donne l'adresse de fin de session que le navigateur ouvrira.
     */
    private final class ApresDeconnexion extends OidcClientInitiatedLogoutSuccessHandler {

        ApresDeconnexion() {
            super(clients);
            setPostLogoutRedirectUri(proprietes.urlPublique() + "/");
            setDefaultTargetUrl(proprietes.urlPublique() + "/");
        }

        @Override
        public void onLogoutSuccess(
                HttpServletRequest requete,
                HttpServletResponse reponse,
                Authentication authentification)
                throws IOException {
            String url = determineTargetUrl(requete, reponse, authentification);
            reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.getWriter()
                    .write("{\"url\":\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
        }
    }
}
