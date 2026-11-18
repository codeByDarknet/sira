package bf.sira.passerelle.config;

import bf.sira.commun.identite.Autorites;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.identite.SecuriteMembres;
import bf.sira.commun.jetons.JetonsRoyaume;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Trois familles de routes, trois populations qui ne se croisent jamais (contrat §4 et §7) : les
 * citoyens par l'espace citoyen, les administrateurs par la console, les services par leur
 * certificat. Chaque préfixe n'accepte qu'un émetteur de jetons ; tout le reste est refusé.
 */
@Configuration(proxyBeanMethods = false)
class Securite {

    static final String INSTRUCTEUR = "instructeur";
    static final String AUTORITE = "autorite";
    static final String AUDITEUR = "auditeur";

    /** Sonde de santé du conteneur : port de gestion, en boucle locale uniquement. */
    @Bean
    @Order(0)
    SecurityFilterChain sante(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint());
        SecuriteMembres.apiSansEtat(http);
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain citoyen(HttpSecurity http, JetonsRoyaume jetons, Proprietes proprietes)
            throws Exception {
        http.securityMatcher("/v1/citoyen/**");
        Proprietes.Identite identite = proprietes.identite();
        var membreEtJeton =
                JetonsRoyaume.appliquer(
                        http,
                        jetons.decodeur(
                                identite.citoyens().emetteur(),
                                identite.citoyens().cles(),
                                identite.audience()),
                        IdentiteMembre.ESPACE_CITOYEN);
        http.authorizeHttpRequests(a -> a.anyRequest().access(membreEtJeton));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain administration(
            HttpSecurity http, JetonsRoyaume jetons, Proprietes proprietes) throws Exception {
        http.securityMatcher("/v1/admin/**");
        Proprietes.Identite identite = proprietes.identite();
        var membreEtJeton =
                JetonsRoyaume.appliquer(
                        http,
                        jetons.decodeur(
                                identite.socle().emetteur(),
                                identite.socle().cles(),
                                identite.audience()),
                        IdentiteMembre.CONSOLE);
        http.authorizeHttpRequests(a ->
                a.requestMatchers(HttpMethod.GET, "/v1/admin/**")
                        .access(avecRole(membreEtJeton, INSTRUCTEUR, AUTORITE, AUDITEUR))
                        // Décider n'appartient qu'à l'autorité ; préparer, qu'à l'instructeur.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/v1/admin/habilitations/*/approbation",
                                "/v1/admin/habilitations/*/rejet",
                                "/v1/admin/habilitations/*/suspension")
                        .access(avecRole(membreEtJeton, AUTORITE))
                        // Où vont le citoyen et les questions : décision de l'autorité aussi.
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/v1/admin/membres/*/retours",
                                "/v1/admin/registres/*/adresse")
                        .access(avecRole(membreEtJeton, AUTORITE))
                        .requestMatchers(HttpMethod.POST, "/v1/admin/signalements/*/traitement")
                        .access(avecRole(membreEtJeton, INSTRUCTEUR, AUTORITE))
                        .anyRequest()
                        .access(avecRole(membreEtJeton, INSTRUCTEUR)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain services(HttpSecurity http) throws Exception {
        SecuriteMembres.appliquer(http);
        http.authorizeHttpRequests(a ->
                a.requestMatchers("/v1/demandes/**", "/v1/demarches/**")
                        .hasAuthority(Autorites.SERVICE)
                        .anyRequest()
                        .denyAll());
        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> avecRole(
            AuthorizationManager<RequestAuthorizationContext> membreEtJeton, String... roles) {
        return AuthorizationManagers.allOf(
                membreEtJeton, AuthorityAuthorizationManager.hasAnyRole(roles));
    }
}
