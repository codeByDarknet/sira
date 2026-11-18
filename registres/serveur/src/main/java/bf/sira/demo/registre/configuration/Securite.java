package bf.sira.demo.registre.configuration;

import bf.sira.commun.agents.AccesAgents;
import bf.sira.commun.agents.CataloguePermissions;
import bf.sira.commun.agents.Permission;
import bf.sira.commun.agents.RoutesAgents;
import bf.sira.commun.oidc.ConnexionOidc;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deux mondes sur le même port : les API de lecture (l'API interne et le contrat commun {@code
 * /v1/}), naïves comme l'est le système ancien (c'est le réseau privé de l'administration qui les
 * protège, et le connecteur qui les garde), et le back-office, réservé aux agents de cette
 * administration. Dans le back-office, chaque route exige un droit, accordé par le rôle de l'agent.
 */
@Configuration(proxyBeanMethods = false)
class Securite {

    static final String PERSONNES_LIRE = "PERSONNES_LIRE";
    static final String PERSONNES_AJOUTER = "PERSONNES_AJOUTER";
    static final String PERSONNES_MODIFIER = "PERSONNES_MODIFIER";
    static final String PERSONNES_SUPPRIMER = "PERSONNES_SUPPRIMER";
    static final String DOCUMENTS_LIRE = "DOCUMENTS_LIRE";
    static final String DOCUMENTS_DEPOSER = "DOCUMENTS_DEPOSER";
    static final String APPELS_LIRE = "APPELS_LIRE";
    static final String PANNES_LIRE = "PANNES_LIRE";
    static final String PANNES_GERER = "PANNES_GERER";

    /** Les droits propres au registre ; ceux des utilisateurs et des rôles s'y ajoutent. */
    @Bean
    CataloguePermissions permissionsRegistre() {
        return () ->
                List.of(
                        new Permission(
                                PERSONNES_LIRE, "Voir les personnes et leurs fiches", "Personnes"),
                        new Permission(PERSONNES_AJOUTER, "Ajouter une personne", "Personnes"),
                        new Permission(PERSONNES_MODIFIER, "Modifier une fiche", "Personnes"),
                        new Permission(PERSONNES_SUPPRIMER, "Supprimer une personne", "Personnes"),
                        new Permission(
                                DOCUMENTS_LIRE, "Voir et télécharger les documents", "Documents"),
                        new Permission(
                                DOCUMENTS_DEPOSER,
                                "Déposer et retirer les documents d’une personne",
                                "Documents"),
                        new Permission(APPELS_LIRE, "Voir les appels reçus de Sira", "Appels"),
                        new Permission(PANNES_LIRE, "Voir l’état du registre", "État du registre"),
                        new Permission(
                                PANNES_GERER, "Changer l’état du registre", "État du registre"));
    }

    @Bean
    @Order(1)
    SecurityFilterChain interne(HttpSecurity http) throws Exception {
        http.securityMatcher("/interne/**", "/v1/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain backOffice(HttpSecurity http, ConnexionOidc connexion, AccesAgents acces)
            throws Exception {
        connexion.appliquer(http);
        http.authorizeHttpRequests(a -> {
            a.requestMatchers("/api/session").authenticated();
            RoutesAgents.appliquer(a, acces);
            a.requestMatchers(HttpMethod.GET, "/api/registre")
                    .access(acces.agent())
                    .requestMatchers(
                            HttpMethod.GET, "/api/personnes/*/pieces", "/api/personnes/*/pieces/*")
                    .access(acces.exige(DOCUMENTS_LIRE))
                    .requestMatchers(HttpMethod.POST, "/api/personnes/*/pieces")
                    .access(acces.exige(DOCUMENTS_DEPOSER))
                    .requestMatchers(HttpMethod.DELETE, "/api/personnes/*/pieces/*")
                    .access(acces.exige(DOCUMENTS_DEPOSER))
                    .requestMatchers(HttpMethod.GET, "/api/personnes/*/documents/*")
                    .access(acces.exige(DOCUMENTS_LIRE))
                    .requestMatchers(HttpMethod.GET, "/api/personnes", "/api/personnes/*")
                    .access(acces.exige(PERSONNES_LIRE))
                    .requestMatchers(HttpMethod.POST, "/api/personnes")
                    .access(acces.exige(PERSONNES_AJOUTER))
                    .requestMatchers(HttpMethod.PATCH, "/api/personnes/*")
                    .access(acces.exige(PERSONNES_MODIFIER))
                    .requestMatchers(HttpMethod.DELETE, "/api/personnes/*")
                    .access(acces.exige(PERSONNES_SUPPRIMER))
                    .requestMatchers(HttpMethod.GET, "/api/appels")
                    .access(acces.exige(APPELS_LIRE))
                    .requestMatchers(HttpMethod.GET, "/api/panne")
                    .access(acces.exige(PANNES_LIRE, PANNES_GERER))
                    .requestMatchers(HttpMethod.PUT, "/api/panne")
                    .access(acces.exige(PANNES_GERER))
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // Toute autre route, même d'agent, est refusée : un droit oublié ferme, il
                    // n'ouvre pas.
                    .anyRequest()
                    .denyAll();
        });
        return http.build();
    }
}
