package bf.sira.commun.jetons;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rôles de royaume portés par un jeton d'accès Keycloak ({@code realm_access.roles}), rendus en
 * autorités {@code ROLE_<rôle>}.
 */
public final class RolesRoyaume implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jeton) {
        return depuis(jeton.getClaims());
    }

    public static Collection<GrantedAuthority> depuis(Map<String, Object> revendications) {
        if (!(revendications.get("realm_access") instanceof Map<?, ?> acces)
                || !(acces.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
