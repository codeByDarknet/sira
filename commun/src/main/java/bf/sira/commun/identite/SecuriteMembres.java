package bf.sira.commun.identite;

import bf.sira.commun.erreurs.Problemes;
import bf.sira.commun.pki.Certificats;
import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authentification des membres par leur certificat client. L'identité vient du SAN du certificat
 * validé par la couche TLS, jamais d'un en-tête ni d'un corps.
 */
public final class SecuriteMembres {

    private static final String ATTRIBUT_CERTIFICAT = "jakarta.servlet.request.X509Certificate";

    private SecuriteMembres() {}

    /**
     * Configure une API sans état appelée par des membres : le certificat client devient le
     * principal {@link Membre}, porteur d'une autorité de {@link Autorites}. Reste à déclarer les
     * règles par route, par exemple {@code
     * .requestMatchers("/v1/demandes/**").hasAuthority(Autorites.SERVICE)}.
     */
    public static void appliquer(HttpSecurity http) throws Exception {
        http.x509(x509 ->
                x509.x509PrincipalExtractor(SecuriteMembres::identiteOuRefus)
                        .authenticationUserDetailsService(jeton ->
                                new Membre(
                                        (IdentiteMembre) jeton.getPrincipal(),
                                        (X509Certificate) jeton.getCredentials())));
        apiSansEtat(http);
    }

    /**
     * Réglages communs aux API appelées par des machines : ni session, ni cookie, erreurs en JSON.
     */
    public static void apiSansEtat(HttpSecurity http) throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(e ->
                        e.authenticationEntryPoint(Problemes.nonAuthentifie())
                                .accessDeniedHandler(Problemes.accesRefuse()));
    }

    /**
     * Exige que le certificat client de la connexion soit celui d'un des membres donnés, quelle que
     * soit l'authentification retenue par ailleurs. Sert aux routes qui demandent à la fois un
     * membre précis et un jeton d'utilisateur.
     */
    public static AuthorizationManager<RequestAuthorizationContext> exige(
            IdentiteMembre... membres) {
        Set<IdentiteMembre> attendus = Set.of(membres);
        return (authentification, contexte) ->
                new AuthorizationDecision(
                        membreDe(contexte.getRequest()).filter(attendus::contains).isPresent());
    }

    /** L'identité portée par le certificat client de la requête, s'il en porte une. */
    public static Optional<IdentiteMembre> membreDe(HttpServletRequest requete) {
        return certificatDe(requete).flatMap(Certificats::uriSan).flatMap(IdentiteMembre::lire);
    }

    /** Le certificat client présenté sur la connexion TLS de la requête. */
    public static Optional<X509Certificate> certificatDe(HttpServletRequest requete) {
        return requete.getAttribute(ATTRIBUT_CERTIFICAT) instanceof X509Certificate[] certificats
                        && certificats.length > 0
                ? Optional.of(certificats[0])
                : Optional.empty();
    }

    private static IdentiteMembre identiteOuRefus(X509Certificate certificat) {
        return Certificats.uriSan(certificat)
                .flatMap(IdentiteMembre::lire)
                .orElseThrow(() -> new BadCredentialsException("certificat sans identité Sira"));
    }
}
