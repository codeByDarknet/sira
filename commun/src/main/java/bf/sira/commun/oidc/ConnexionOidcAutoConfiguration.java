package bf.sira.commun.oidc;

import bf.sira.commun.config.ProprietesCommun;
import bf.sira.commun.config.SiraAutoConfiguration;
import bf.sira.commun.erreurs.Problemes;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jetons.RolesRoyaume;
import bf.sira.commun.mtls.ClientsMtls;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.AbstractOAuth2AuthorizationGrantRequest;
import org.springframework.security.oauth2.client.endpoint.AbstractRestClientOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultOAuth2TokenRequestParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.converter.ClaimTypeConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;

/**
 * Active la connexion OIDC avec session dès que {@code sira.oidc.client-id} est renseigné et que le
 * client OAuth2 de Spring Security est présent. Le serveur est un client confidentiel authentifié
 * par son certificat ({@code tls_client_auth}, RFC 8705) : aucun secret partagé.
 */
@AutoConfiguration(
        after = SiraAutoConfiguration.class,
        beforeName =
                "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration")
@ConditionalOnClass(ClientRegistration.class)
@ConditionalOnProperty("sira.oidc.client-id")
@EnableConfigurationProperties(ProprietesOidc.class)
@Import(SessionControleur.class)
public class ConnexionOidcAutoConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(ProprietesOidc proprietes) {
        ClientRegistration enregistrement =
                ClientRegistration.withRegistrationId(ConnexionOidc.ENREGISTREMENT)
                        .clientId(proprietes.clientId())
                        .clientAuthenticationMethod(ClientAuthenticationMethod.TLS_CLIENT_AUTH)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .clientSettings(
                                ClientRegistration.ClientSettings.builder()
                                        .requireProofKey(true)
                                        .build())
                        .redirectUri(proprietes.urlPublique() + ConnexionOidc.CHEMIN_RETOUR)
                        .scope(proprietes.scopes())
                        .issuerUri(proprietes.emetteur())
                        .authorizationUri(proprietes.autorisationUri())
                        .tokenUri(proprietes.jetonsUri())
                        .jwkSetUri(proprietes.jwksUri())
                        .providerConfigurationMetadata(
                                Map.of("end_session_endpoint", proprietes.finSessionUri()))
                        .userNameAttributeName("sub")
                        .build();
        return new InMemoryClientRegistrationRepository(enregistrement);
    }

    /** Les jetons vivent dans la session : ils disparaissent avec elle. */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /**
     * Le jeton d'identité est vérifié avec les clés du royaume, lues en mTLS à l'adresse interne.
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> decodeurJetonIdentite(ClientsMtls clientsMtls) {
        return new JwtDecoderFactory<>() {
            private volatile JwtDecoder decodeur;

            @Override
            public JwtDecoder createDecoder(ClientRegistration enregistrement) {
                if (decodeur == null) {
                    decodeur = creer(enregistrement, clientsMtls);
                }
                return decodeur;
            }
        };
    }

    private static JwtDecoder creer(ClientRegistration enregistrement, ClientsMtls clientsMtls) {
        NimbusJwtDecoder decodeur =
                NimbusJwtDecoder.withJwkSetUri(enregistrement.getProviderDetails().getJwkSetUri())
                        .restOperations(
                                clientsMtls.restTemplate(IdentiteMembre.FOURNISSEUR_IDENTITE))
                        .jwsAlgorithms(algorithmes ->
                                algorithmes.addAll(
                                        List.of(
                                                SignatureAlgorithm.RS256,
                                                SignatureAlgorithm.PS256,
                                                SignatureAlgorithm.ES256)))
                        .build();
        decodeur.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(), new OidcIdTokenValidator(enregistrement)));
        decodeur.setClaimSetConverter(
                new ClaimTypeConverter(
                        OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()));
        return decodeur;
    }

    /** Client HTTP du point de jetons : le certificat présenté authentifie le client. */
    private static RestClient clientPointDeJetons(ClientsMtls clientsMtls) {
        return RestClient.builder()
                .requestFactory(clientsMtls.fabrique(IdentiteMembre.FOURNISSEUR_IDENTITE))
                .configureMessageConverters(convertisseurs ->
                        convertisseurs
                                .addCustomConverter(new FormHttpMessageConverter())
                                .addCustomConverter(
                                        new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
    }

    /**
     * Avec {@code tls_client_auth}, la requête ne porte que l'identifiant du client (RFC 8705
     * §2.1.1) : c'est le certificat de la connexion qui prouve qui il est. Le convertisseur par
     * défaut l'ajoute déjà ; le déclarer explicitement suffit à faire accepter cette méthode. Un
     * second {@code client_id} rendrait la demande irrecevable pour Keycloak.
     */
    private static <T extends AbstractOAuth2AuthorizationGrantRequest> void regler(
            AbstractRestClientOAuth2AccessTokenResponseClient<T> client, ClientsMtls clientsMtls) {
        client.setRestClient(clientPointDeJetons(clientsMtls));
        client.setParametersConverter(new DefaultOAuth2TokenRequestParametersConverter<>());
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> clientJetons(
            ClientsMtls clientsMtls) {
        RestClientAuthorizationCodeTokenResponseClient client =
                new RestClientAuthorizationCodeTokenResponseClient();
        regler(client, clientsMtls);
        return client;
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clients,
            OAuth2AuthorizedClientRepository jetons,
            ClientsMtls clientsMtls) {
        RestClientRefreshTokenTokenResponseClient renouvellement =
                new RestClientRefreshTokenTokenResponseClient();
        regler(renouvellement, clientsMtls);
        DefaultOAuth2AuthorizedClientManager gestionnaire =
                new DefaultOAuth2AuthorizedClientManager(clients, jetons);
        gestionnaire.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken(r -> r.accessTokenResponseClient(renouvellement))
                        .build());
        return gestionnaire;
    }

    /**
     * Les rôles de royaume ne figurent que dans le jeton d'accès. Il vient d'être reçu du point de
     * jetons sur une connexion mTLS : son contenu est lu sans revérifier sa signature.
     */
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> serviceUtilisateurOidc() {
        OidcUserService delegue = new OidcUserService();
        return demande -> {
            OidcUser utilisateur = delegue.loadUser(demande);
            Set<GrantedAuthority> autorites = new LinkedHashSet<>(utilisateur.getAuthorities());
            try {
                autorites.addAll(
                        RolesRoyaume.depuis(
                                JWTParser.parse(demande.getAccessToken().getTokenValue())
                                        .getJWTClaimsSet()
                                        .getClaims()));
            } catch (ParseException e) {
                // Jeton d'accès opaque : l'utilisateur est connecté, sans rôle.
            }
            return new DefaultOidcUser(
                    autorites, utilisateur.getIdToken(), utilisateur.getUserInfo(), "sub");
        };
    }

    @Bean
    ConnexionOidc connexionOidc(
            ProprietesOidc proprietes,
            ClientRegistrationRepository clients,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> clientJetons,
            OAuth2UserService<OidcUserRequest, OidcUser> serviceUtilisateur) {
        return new ConnexionOidc(proprietes, clients, clientJetons, serviceUtilisateur);
    }

    @Bean
    RelaisPasserelle relaisPasserelle(
            ProprietesCommun proprietes,
            ClientsMtls clientsMtls,
            OAuth2AuthorizedClientManager gestionnaire,
            OAuth2AuthorizedClientRepository jetons) {
        OAuth2ClientHttpRequestInterceptor jetonUtilisateur =
                new OAuth2ClientHttpRequestInterceptor(gestionnaire);
        jetonUtilisateur.setClientRegistrationIdResolver(requete -> ConnexionOidc.ENREGISTREMENT);
        // Un 401 de la passerelle retire le jeton de la session : l'appel suivant en demandera un
        // neuf.
        jetonUtilisateur.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(jetons));
        return new RelaisPasserelle(
                clientsMtls
                        .constructeur(IdentiteMembre.PASSERELLE)
                        .baseUrl(proprietes.passerelle().url())
                        .requestInterceptor(jetonUtilisateur)
                        .build());
    }

    /**
     * Session du fournisseur terminée ou jeton non renouvelable : l'interface doit reconnecter.
     * Passe avant le gestionnaire commun, qui en ferait une erreur interne.
     */
    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class JetonIndisponible {

        @ExceptionHandler(ClientAuthorizationException.class)
        ResponseEntity<ProblemDetail> jetonIndisponible() {
            ProblemDetail probleme = Problemes.pour(HttpStatus.UNAUTHORIZED);
            return ResponseEntity.status(probleme.getStatus())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(probleme);
        }
    }
}
