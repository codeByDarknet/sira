package bf.sira.console.securite;

import bf.sira.commun.oidc.ConnexionOidc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Tout passe par la session de l'administrateur : aucun jeton présenté par un navigateur n'est
 * accepté. Le nginx de l'interface ne relaie que {@code /api/} ; le reste est fermé.
 */
@Configuration(proxyBeanMethods = false)
class SecuriteConfiguration {

    @Bean
    SecurityFilterChain securite(HttpSecurity http, ConnexionOidc connexion) throws Exception {
        connexion.appliquer(http);
        http.authorizeHttpRequests(regles ->
                        regles
                                // Sonde du conteneur, servie sur le port de gestion local.
                                .requestMatchers("/actuator/health/**")
                                .permitAll()
                                .requestMatchers("/api/**")
                                .authenticated()
                                .anyRequest()
                                .denyAll())
                .headers(enTetes ->
                        enTetes.contentSecurityPolicy(csp ->
                                        csp.policyDirectives(
                                                "default-src 'none'; frame-ancestors 'none'"))
                                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
