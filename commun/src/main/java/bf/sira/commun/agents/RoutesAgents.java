package bf.sira.commun.agents;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Les droits exigés par les routes des utilisateurs et des rôles. À appeler dans la chaîne de
 * sécurité de l'application, avant sa règle générale sur {@code /api/**}.
 */
public final class RoutesAgents {

    private RoutesAgents() {}

    public static void appliquer(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry regles,
            AccesAgents acces) {
        regles.requestMatchers(HttpMethod.GET, "/api/utilisateurs", "/api/utilisateurs/*")
                .access(acces.exige(Permission.UTILISATEURS_LIRE, Permission.UTILISATEURS_GERER))
                .requestMatchers("/api/utilisateurs", "/api/utilisateurs/**")
                .access(acces.exige(Permission.UTILISATEURS_GERER))
                // La liste des rôles sert aussi au formulaire des utilisateurs.
                .requestMatchers(HttpMethod.GET, "/api/roles", "/api/roles/*", "/api/permissions")
                .access(
                        acces.exige(
                                Permission.ROLES_LIRE,
                                Permission.ROLES_GERER,
                                Permission.UTILISATEURS_GERER))
                .requestMatchers("/api/roles", "/api/roles/**")
                .access(acces.exige(Permission.ROLES_GERER));
    }
}
