package bf.sira.demo.service.configuration;

import bf.sira.commun.agents.AccesAgents;
import bf.sira.commun.agents.CataloguePermissions;
import bf.sira.commun.agents.Permission;
import bf.sira.commun.agents.RoutesAgents;
import bf.sira.commun.oidc.ConnexionOidc;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deux publics sur la même plateforme. Le citoyen n'y a pas de compte : c'est Sira qui l'identifie,
 * et son navigateur n'est relié à sa demande que par une session anonyme. L'agent, lui, se connecte
 * à l'annuaire de son administration, et chaque route de l'espace agent exige un droit de son rôle.
 */
@Configuration(proxyBeanMethods = false)
class Securite {

    static final String DOSSIERS_LIRE = "DOSSIERS_LIRE";
    static final String PIECES_LIRE = "PIECES_LIRE";
    static final String VERIFICATIONS_DEMANDER = "VERIFICATIONS_DEMANDER";

    /** Les droits propres au service ; ceux des utilisateurs et des rôles s'y ajoutent. */
    @Bean
    CataloguePermissions permissionsService() {
        return () ->
                List.of(
                        new Permission(DOSSIERS_LIRE, "Voir les dossiers reçus", "Dossiers"),
                        new Permission(
                                PIECES_LIRE,
                                "Voir et télécharger les pièces d’un dossier",
                                "Dossiers"),
                        new Permission(
                                VERIFICATIONS_DEMANDER,
                                "Redemander une vérification au citoyen",
                                "Dossiers"));
    }

    @Bean
    SecurityFilterChain chaine(HttpSecurity http, ConnexionOidc connexion, AccesAgents acces)
            throws Exception {
        connexion.appliquer(http);
        http.authorizeHttpRequests(a -> {
            a.requestMatchers("/api/demarche", "/api/parcours/**")
                    .permitAll()
                    .requestMatchers("/api/session")
                    .authenticated();
            RoutesAgents.appliquer(a, acces);
            a.requestMatchers(
                            HttpMethod.GET,
                            "/api/agent/dossiers/*/pieces/*",
                            "/api/agent/dossiers/*/declarations/*/piece")
                    .access(acces.exige(PIECES_LIRE))
                    .requestMatchers(HttpMethod.GET, "/api/agent/dossiers", "/api/agent/dossiers/*")
                    .access(acces.exige(DOSSIERS_LIRE))
                    .requestMatchers(HttpMethod.POST, "/api/agent/dossiers/*/verifications")
                    .access(acces.exige(VERIFICATIONS_DEMANDER))
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // Toute autre route est refusée : un droit oublié ferme, il n'ouvre pas.
                    .anyRequest()
                    .denyAll();
        });
        return http.build();
    }
}
