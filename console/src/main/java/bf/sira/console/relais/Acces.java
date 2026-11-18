package bf.sira.console.relais;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Ce qu'une route de la console exige, d'après les rôles du royaume {@code sira-socle} (contrat
 * §4.6). C'est un premier contrôle : la passerelle refait le sien, qui seul fait foi.
 */
enum Acces {
    /** Consulter : tout rôle de la console. */
    LECTURE("instructeur", "autorite", "auditeur"),
    /** Préparer et modifier : l'instructeur. */
    ECRITURE("instructeur"),
    /** Approuver, rejeter, suspendre : l'autorité. */
    DECISION("autorite"),
    /** Répondre à un signalement : l'instructeur ou l'autorité. */
    TRAITEMENT("instructeur", "autorite");

    private final Set<String> autorites;

    Acces(String... roles) {
        this.autorites =
                Arrays.stream(roles).map(role -> "ROLE_" + role).collect(Collectors.toSet());
    }

    boolean accordeA(Collection<? extends GrantedAuthority> detenues) {
        return detenues.stream().map(GrantedAuthority::getAuthority).anyMatch(autorites::contains);
    }
}
