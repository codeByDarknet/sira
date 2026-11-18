package bf.sira.commun.jetons;

import bf.sira.commun.erreurs.Problemes;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.identite.SecuriteMembres;
import bf.sira.commun.mtls.ClientsMtls;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Validation des jetons d'utilisateur côté ressource : un seul émetteur par chaîne de filtres,
 * l'audience attendue, et la liaison au certificat du serveur d'application qui relaie le jeton.
 */
public final class JetonsRoyaume {

    private final ClientsMtls clients;

    public JetonsRoyaume(ClientsMtls clients) {
        this.clients = clients;
    }

    /**
     * @param emetteur valeur exacte de {@code iss} : l'adresse publique du royaume
     * @param jwksUri adresse interne des clés du royaume, lue en mTLS
     * @param audience valeur exigée dans {@code aud}
     */
    public JwtDecoder decodeur(String emetteur, String jwksUri, String audience) {
        NimbusJwtDecoder decodeur =
                NimbusJwtDecoder.withJwkSetUri(jwksUri)
                        .restOperations(clients.restTemplate(IdentiteMembre.FOURNISSEUR_IDENTITE))
                        .jwsAlgorithms(algorithmes -> {
                            algorithmes.add(SignatureAlgorithm.RS256);
                            algorithmes.add(SignatureAlgorithm.PS256);
                            algorithmes.add(SignatureAlgorithm.ES256);
                        })
                        .build();
        decodeur.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(emetteur),
                        new JwtAudienceValidator(audience),
                        new LiaisonCertificat()));
        return decodeur;
    }

    /**
     * Configure une chaîne de filtres sans état dont chaque requête doit venir du membre donné
     * <em>et</em> porter un jeton valide du royaume. Les rôles du royaume deviennent des autorités
     * {@code ROLE_<rôle>} ; le principal est le {@code Jwt}.
     *
     * @return la règle « membre et jeton », à passer à {@code .access(...)} ou à combiner par
     *     {@code AuthorizationManagers.allOf(regle, AuthorityAuthorizationManager.hasRole(...))}
     */
    public static AuthorizationManager<RequestAuthorizationContext> appliquer(
            HttpSecurity http, JwtDecoder decodeur, IdentiteMembre membre) throws Exception {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(new RolesRoyaume());
        SecuriteMembres.apiSansEtat(http);
        http.oauth2ResourceServer(ressource ->
                ressource
                        .jwt(jwt -> jwt.decoder(decodeur).jwtAuthenticationConverter(convertisseur))
                        .authenticationEntryPoint(Problemes.nonAuthentifie())
                        .accessDeniedHandler(Problemes.accesRefuse()));
        return AuthorizationManagers.allOf(
                SecuriteMembres.exige(membre), AuthenticatedAuthorizationManager.authenticated());
    }
}
