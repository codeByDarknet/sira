package bf.sira.commun.identite;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Le membre appelant, authentifié par son certificat client. S'obtient dans un contrôleur par
 * {@code @AuthenticationPrincipal Membre membre}.
 */
public record Membre(IdentiteMembre identite, X509Certificate certificat) implements UserDetails {

    public String uri() {
        return identite.uri();
    }

    public String code() {
        return identite.code();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(identite.autorite()));
    }

    @Override
    public String getUsername() {
        return identite.uri();
    }

    /** Un membre n'a pas de mot de passe : seule la possession de la clé privée l'authentifie. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String toString() {
        return identite.uri();
    }
}
