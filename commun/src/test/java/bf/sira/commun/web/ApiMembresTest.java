package bf.sira.commun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.identite.Autorites;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.identite.Membre;
import bf.sira.commun.identite.SecuriteMembres;
import bf.sira.commun.jetons.JetonsRoyaume;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Une API en mode {@code complet}, appelée sur une vraie connexion TLS : certificat client exigé,
 * identité tirée du SAN, jetons d'utilisateur liés au certificat, erreurs sans fuite.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"sira.mtls.mode=complet", "sira.http.taille-corps-max=2KB"})
class ApiMembresTest {

    private static final IdentiteMembre SERVICE = IdentiteMembre.service("demo-bourse");
    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur FOURNISSEUR = new FauxFournisseur(PKI);
    private static final Path REPERTOIRE = repertoire();

    @LocalServerPort private int port;

    private static Path repertoire() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.PASSERELLE);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", REPERTOIRE::toString);
    }

    @AfterAll
    static void arreter() {
        FOURNISSEUR.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Routes.class)
    static class Appli {

        @Bean
        @Order(1)
        SecurityFilterChain routesCitoyen(HttpSecurity http, JetonsRoyaume jetons)
                throws Exception {
            http.securityMatcher("/v1/citoyen/**");
            var membreEtJeton =
                    JetonsRoyaume.appliquer(
                            http,
                            jetons.decodeur(
                                    FauxFournisseur.EMETTEUR,
                                    FOURNISSEUR.urlInterne() + "/protocol/openid-connect/certs",
                                    FauxFournisseur.AUDIENCE),
                            IdentiteMembre.ESPACE_CITOYEN);
            http.authorizeHttpRequests(a -> a.anyRequest().access(membreEtJeton));
            return http.build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain routesMembres(HttpSecurity http) throws Exception {
            SecuriteMembres.appliquer(http);
            http.authorizeHttpRequests(a ->
                    a.requestMatchers("/v1/services/**")
                            .hasAuthority(Autorites.SERVICE)
                            .anyRequest()
                            .denyAll());
            return http.build();
        }
    }

    record Inscription(@NotBlank @Size(max = 2000) String nomComplet) {}

    @RestController
    static class Routes {

        @GetMapping("/v1/services/moi")
        Map<String, String> moi(@AuthenticationPrincipal Membre membre) {
            return Map.of("identiteMembre", membre.uri());
        }

        @PostMapping("/v1/services/inscriptions")
        Inscription inscrire(@Valid @RequestBody Inscription inscription) {
            return inscription;
        }

        @GetMapping("/v1/services/panne")
        String panne() {
            throw new IllegalStateException("mot de passe de la base : secret-interne");
        }

        @GetMapping("/v1/services/absent")
        String absent() {
            throw ErreurMetier.introuvable();
        }

        @GetMapping("/v1/citoyen/moi")
        Map<String, Object> citoyen(@AuthenticationPrincipal Jwt jeton, Authentication a) {
            List<String> roles =
                    a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
            return Map.of("sub", jeton.getSubject(), "roles", roles);
        }
    }

    private RestClient client(SslBundle bundle) {
        return new ClientsMtls(
                        bundle, Duration.ofSeconds(2), Duration.ofSeconds(5), RestClient::builder)
                .constructeur(IdentiteMembre.PASSERELLE)
                .baseUrl("https://localhost:" + port)
                .defaultStatusHandler(statut -> true, (requete, reponse) -> {})
                .build();
    }

    private RestClient clientDe(IdentiteMembre membre) {
        return client(PKI.bundle(PKI.tls(membre)));
    }

    @Test
    void unServiceEstReconnuParSonCertificat() {
        ResponseEntity<String> reponse =
                clientDe(SERVICE).get().uri("/v1/services/moi").retrieve().toEntity(String.class);

        assertThat(reponse.getStatusCode().value()).isEqualTo(200);
        assertThat(reponse.getBody())
                .isEqualTo("{\"identiteMembre\":\"urn:sira:service:demo-bourse\"}");
    }

    @Test
    void unMembreDUnAutreTypeEstRefuse() {
        ResponseEntity<String> reponse =
                clientDe(IdentiteMembre.registre("etat-civil"))
                        .get()
                        .uri("/v1/services/moi")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(reponse.getStatusCode().value()).isEqualTo(403);
        assertThat(reponse.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
        assertThat(reponse.getBody()).contains("urn:sira:erreur:acces-refuse");
    }

    @Test
    void sansCertificatClientLaConnexionEstRefusee() {
        RestClient anonyme = client(PKI.bundleSansCertificat());

        assertThatThrownBy(() ->
                        anonyme.get().uri("/v1/services/moi").retrieve().toEntity(String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void unCertificatDUneAutreAutoriteEstRefuse() {
        Bicle etranger = PkiDeTest.creer().tls(SERVICE);
        RestClient intrus = client(PkiDeTest.bundle(etranger, PKI.autorite()));

        assertThatThrownBy(() ->
                        intrus.get().uri("/v1/services/moi").retrieve().toEntity(String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void seulTls13EstAccepte() throws IOException {
        var fabrique = PKI.contexteTls(PKI.tls(SERVICE)).getSocketFactory();
        try (SSLSocket connexion = (SSLSocket) fabrique.createSocket("localhost", port)) {
            connexion.setEnabledProtocols(new String[] {"TLSv1.2"});

            assertThatThrownBy(connexion::startHandshake).isInstanceOf(IOException.class);
        }
    }

    @Test
    void lesChampsInvalidesSontNommesSansLeurValeur() {
        ResponseEntity<String> reponse =
                clientDe(SERVICE)
                        .post()
                        .uri("/v1/services/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"nom_complet\":\"  \"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(reponse.getStatusCode().value()).isEqualTo(400);
        assertThat(reponse.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
        assertThat(reponse.getBody())
                .contains("urn:sira:erreur:requete-invalide")
                .contains("\"champ\":\"nomComplet\"")
                .contains("\"regle\":\"NotBlank\"");
    }

    @Test
    void lesCorpsJsonSontEnSnakeCase() {
        ResponseEntity<String> reponse =
                clientDe(SERVICE)
                        .post()
                        .uri("/v1/services/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"nom_complet\":\"Awa\"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(reponse.getBody()).isEqualTo("{\"nom_complet\":\"Awa\"}");
    }

    @Test
    void unCorpsTropVolumineuxEstRefuse() {
        ResponseEntity<String> reponse =
                clientDe(SERVICE)
                        .post()
                        .uri("/v1/services/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"nom_complet\":\"" + "a".repeat(4000) + "\"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(reponse.getStatusCode().value()).isEqualTo(413);
        assertThat(reponse.getBody()).contains("urn:sira:erreur:corps-trop-volumineux");
    }

    @Test
    void uneErreurInattendueNeFuitPas() {
        ResponseEntity<String> reponse =
                clientDe(SERVICE).get().uri("/v1/services/panne").retrieve().toEntity(String.class);

        assertThat(reponse.getStatusCode().value()).isEqualTo(500);
        assertThat(reponse.getBody())
                .contains("urn:sira:erreur:erreur-interne")
                .contains("\"reference\"")
                .doesNotContain("secret-interne")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void lesErreursMetierEtDeRoutageSontDesProblemes() {
        RestClient service = clientDe(SERVICE);

        ResponseEntity<String> absent =
                service.get().uri("/v1/services/absent").retrieve().toEntity(String.class);
        ResponseEntity<String> inconnue =
                service.get().uri("/v1/services/inconnue").retrieve().toEntity(String.class);
        ResponseEntity<String> methode =
                service.delete().uri("/v1/services/moi").retrieve().toEntity(String.class);
        ResponseEntity<String> illisible =
                service.post()
                        .uri("/v1/services/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{pas du json")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(absent.getStatusCode().value()).isEqualTo(404);
        assertThat(absent.getBody()).contains("urn:sira:erreur:introuvable");
        assertThat(inconnue.getStatusCode().value()).isEqualTo(404);
        assertThat(inconnue.getBody()).contains("urn:sira:erreur:introuvable");
        assertThat(methode.getStatusCode().value()).isEqualTo(405);
        assertThat(methode.getBody()).contains("urn:sira:erreur:methode-refusee");
        assertThat(illisible.getStatusCode().value()).isEqualTo(400);
        assertThat(illisible.getBody()).doesNotContain("pas du json").doesNotContain("Jackson");
    }

    private ResponseEntity<String> appelCitoyen(Bicle tls, String jeton) {
        var requete = client(PKI.bundle(tls)).get().uri("/v1/citoyen/moi");
        if (jeton != null) {
            requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
        }
        return requete.retrieve().toEntity(String.class);
    }

    @Test
    void unJetonLieAuCertificatDeLEspaceCitoyenEstAccepte() {
        Bicle tls = PKI.tls(IdentiteMembre.ESPACE_CITOYEN);

        ResponseEntity<String> reponse =
                appelCitoyen(tls, FOURNISSEUR.jetonAcces(tls.certificat()));

        assertThat(reponse.getStatusCode().value()).isEqualTo(200);
        assertThat(reponse.getBody()).contains("\"sub\":\"citoyen-1\"").contains("ROLE_citoyen");
    }

    @Test
    void unJetonVoleEstInutilisableDepuisUnAutreCertificat() {
        Bicle titulaire = PKI.tls(IdentiteMembre.ESPACE_CITOYEN);
        Bicle autre = PKI.tls(IdentiteMembre.ESPACE_CITOYEN);
        String jeton = FOURNISSEUR.jetonAcces(titulaire.certificat());

        assertThat(appelCitoyen(autre, jeton).getStatusCode().value()).isEqualTo(401);
        assertThat(
                        appelCitoyen(
                                        autre,
                                        FOURNISSEUR.jetonAcces(
                                                autre.certificat(), r -> r.claim("cnf", null)))
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
    }

    @Test
    void unJetonDUnAutreEmetteurDUneAutreAudienceOuExpireEstRefuse() {
        Bicle tls = PKI.tls(IdentiteMembre.ESPACE_CITOYEN);
        String autreEmetteur =
                FOURNISSEUR.jetonAcces(
                        tls.certificat(), r -> r.issuer("http://identite.test/realms/demo-agents"));
        String autreAudience =
                FOURNISSEUR.jetonAcces(tls.certificat(), r -> r.audience("sira-console"));
        String expire =
                FOURNISSEUR.jetonAcces(
                        tls.certificat(),
                        r -> r.expirationTime(Date.from(Instant.now().minusSeconds(600))));

        assertThat(appelCitoyen(tls, autreEmetteur).getStatusCode().value()).isEqualTo(401);
        assertThat(appelCitoyen(tls, autreAudience).getStatusCode().value()).isEqualTo(401);
        assertThat(appelCitoyen(tls, expire).getStatusCode().value()).isEqualTo(401);
        assertThat(appelCitoyen(tls, "pas-un-jeton").getStatusCode().value()).isEqualTo(401);
        ResponseEntity<String> sansJeton = appelCitoyen(tls, null);
        assertThat(sansJeton.getStatusCode().value()).isEqualTo(401);
        assertThat(sansJeton.getBody()).contains("urn:sira:erreur:non-authentifie");
    }

    @Test
    void unJetonValidePresenteParUnAutreMembreEstRefuse() {
        Bicle service = PKI.tls(SERVICE);

        ResponseEntity<String> reponse =
                appelCitoyen(service, FOURNISSEUR.jetonAcces(service.certificat()));

        assertThat(reponse.getStatusCode().value()).isEqualTo(403);
    }
}
