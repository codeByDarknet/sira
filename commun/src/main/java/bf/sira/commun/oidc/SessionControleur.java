package bf.sira.commun.oidc;

import bf.sira.commun.agents.AccesAgents;
import bf.sira.commun.erreurs.ErreurMetier;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dit à l'interface qui est connecté. Appelée au chargement, elle pose aussi le cookie CSRF. */
@RestController
class SessionControleur {

    private static final String PREFIXE_ROLE = "ROLE_";

    /**
     * Ce que l'interface affiche de la personne connectée, sur sa page de profil : nom, identifiant
     * de connexion, adresse de messagerie (si connue) et rôles.
     */
    record Session(
            String nom,
            String identifiant,
            String courriel,
            List<String> roles,
            List<String> permissions) {}

    private final ObjectProvider<AccesAgents> acces;

    SessionControleur(ObjectProvider<AccesAgents> acces) {
        this.acces = acces;
    }

    @GetMapping("/api/session")
    Session session(
            @AuthenticationPrincipal OidcUser utilisateur, Authentication authentification) {
        if (utilisateur == null) {
            throw new ErreurMetier(
                    HttpStatus.UNAUTHORIZED, "non-authentifie", "Authentification requise");
        }
        List<String> roles =
                utilisateur.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(autorite -> autorite.startsWith(PREFIXE_ROLE))
                        .map(autorite -> autorite.substring(PREFIXE_ROLE.length()))
                        .sorted()
                        .toList();
        String nom = utilisateur.getFullName();
        return new Session(
                nom != null ? nom : utilisateur.getPreferredUsername(),
                utilisateur.getPreferredUsername(),
                utilisateur.getEmail(),
                roles,
                permissions(authentification));
    }

    /** Les droits de l'agent (registres et services) ; aucun ailleurs. */
    private List<String> permissions(Authentication authentification) {
        AccesAgents agents = acces.getIfAvailable();
        return agents == null
                ? List.of()
                : agents.permissions(authentification).stream().sorted().toList();
    }
}
