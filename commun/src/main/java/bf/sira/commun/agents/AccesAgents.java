package bf.sira.commun.agents;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Qui entre, et ce qu'il peut faire. Deux conditions, toujours ensemble : l'agent appartient à
 * cette administration (attribut {@code administration} de l'annuaire, commun à toutes), et son
 * rôle lui donne le droit demandé. Les droits sont relus à chaque requête : un rôle retiré prend
 * effet aussitôt, sans attendre une nouvelle connexion.
 */
public class AccesAgents {

    static final String REVENDICATION_ADMINISTRATION = "administration";

    private final String administration;
    private final DepotRoles roles;
    private final List<Permission> catalogue;

    public AccesAgents(String administration, DepotRoles roles, CataloguePermissions propres) {
        this.administration = administration;
        this.roles = roles;
        this.catalogue =
                Stream.concat(propres.permissions().stream(), Permission.COMMUNES.stream())
                        .toList();
    }

    /** Tous les droits de cette application : ceux du métier, puis utilisateurs et rôles. */
    public List<Permission> catalogue() {
        return catalogue;
    }

    /** L'agent connecté appartient à cette administration (aucun droit particulier exigé). */
    public AuthorizationManager<RequestAuthorizationContext> agent() {
        return (authentification, contexte) ->
                new AuthorizationDecision(identifiant(authentification.get()) != null);
    }

    /** L'agent appartient à cette administration et a au moins un des droits cités. */
    public AuthorizationManager<RequestAuthorizationContext> exige(String... permissions) {
        return (authentification, contexte) -> {
            String identifiant = identifiant(authentification.get());
            if (identifiant == null) {
                return new AuthorizationDecision(false);
            }
            Set<String> accordees = roles.permissionsDe(identifiant);
            return new AuthorizationDecision(Stream.of(permissions).anyMatch(accordees::contains));
        };
    }

    /** Les droits de l'agent connecté ; aucun s'il n'est pas de cette administration. */
    public Set<String> permissions(Authentication authentification) {
        String identifiant = identifiant(authentification);
        return identifiant == null ? Set.of() : roles.permissionsDe(identifiant);
    }

    /** Identifiant de connexion de l'agent, s'il est de cette administration ; sinon null. */
    String identifiant(Authentication authentification) {
        if (authentification != null
                && authentification.isAuthenticated()
                && authentification.getPrincipal() instanceof OidcUser agent
                && administration.equals(agent.getClaimAsString(REVENDICATION_ADMINISTRATION))) {
            return agent.getPreferredUsername();
        }
        return null;
    }
}
