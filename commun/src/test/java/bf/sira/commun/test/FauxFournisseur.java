package bf.sira.commun.test;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.net.ssl.SSLParameters;

/**
 * Fournisseur d'identité simulé pour les tests : point de jetons et clés publiques servis en mTLS,
 * comme le Keycloak du socle sur le réseau interne. Il sert aussi de fausse passerelle ({@code
 * /v1/echo}) pour observer ce qu'un relais lui présente.
 */
public final class FauxFournisseur implements AutoCloseable {

    private static final String ROYAUME = "/realms/test";
    private static final String PROTOCOLE = ROYAUME + "/protocol/openid-connect";

    /** Adresse publique du royaume, telle que la voit le navigateur : valeur de {@code iss}. */
    public static final String EMETTEUR = "http://identite.test" + ROYAUME;

    public static final String AUDIENCE = "sira-passerelle";

    private final HttpsServer serveur;
    private final RSAKey cle;
    private volatile String nonce;
    private volatile Map<String, String> derniereDemande = Map.of();
    private volatile X509Certificate dernierCertificat;
    private volatile String dernierEnTeteAuthorization;

    /** Un fournisseur qui présente l'identité du Keycloak du socle. */
    public FauxFournisseur(PkiDeTest pki) {
        this(pki, IdentiteMembre.FOURNISSEUR_IDENTITE);
    }

    /** Un serveur qui présente l'identité {@code identite}, par exemple une fausse passerelle. */
    public FauxFournisseur(PkiDeTest pki, IdentiteMembre identite) {
        Bicle tls = pki.tls(identite);
        try {
            this.cle = new RSAKeyGenerator(2048).keyID("test").generate();
            this.serveur =
                    HttpsServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.setHttpsConfigurator(
                new HttpsConfigurator(pki.contexteTls(tls)) {
                    @Override
                    public void configure(HttpsParameters parametres) {
                        SSLParameters tls = getSSLContext().getDefaultSSLParameters();
                        tls.setNeedClientAuth(true);
                        parametres.setSSLParameters(tls);
                    }
                });
        serveur.createContext(
                PROTOCOLE + "/certs",
                echange -> repondre(echange, new JWKSet(cle.toPublicJWK()).toString()));
        serveur.createContext(PROTOCOLE + "/token", this::jetons);
        serveur.createContext("/v1/echo", this::echo);
        serveur.start();
    }

    /** Adresse du royaume sur le « réseau interne » du test. */
    public String urlInterne() {
        return racine() + ROYAUME;
    }

    public String racine() {
        return "https://localhost:" + serveur.getAddress().getPort();
    }

    /** Le {@code nonce} de la demande d'autorisation, que le jeton d'identité doit reprendre. */
    public void nonce(String nonce) {
        this.nonce = nonce;
    }

    /** Paramètres du dernier appel au point de jetons. */
    public Map<String, String> derniereDemande() {
        return derniereDemande;
    }

    /** Certificat client du dernier appel reçu. */
    public X509Certificate dernierCertificat() {
        return dernierCertificat;
    }

    /** En-tête {@code Authorization} du dernier appel reçu, ou {@code null}. */
    public String dernierEnTeteAuthorization() {
        return dernierEnTeteAuthorization;
    }

    /**
     * Un jeton d'accès du royaume, lié au certificat donné, porteur du rôle {@code citoyen}.
     *
     * @param retouches modifie les revendications avant signature, pour fabriquer un jeton à
     *     refuser
     */
    public String jetonAcces(X509Certificate titulaire, Consumer<JWTClaimsSet.Builder> retouches) {
        Instant maintenant = Instant.now();
        JWTClaimsSet.Builder revendications =
                new JWTClaimsSet.Builder()
                        .issuer(EMETTEUR)
                        .subject("citoyen-1")
                        .audience(AUDIENCE)
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plusSeconds(300)))
                        .claim("realm_access", Map.of("roles", List.of("citoyen")))
                        .claim("cnf", Map.of("x5t#S256", Certificats.empreinteSha256(titulaire)));
        retouches.accept(revendications);
        return signer(revendications.build());
    }

    public String jetonAcces(X509Certificate titulaire) {
        return jetonAcces(titulaire, revendications -> {});
    }

    private String jetonIdentite(String client) {
        Instant maintenant = Instant.now();
        return signer(
                new JWTClaimsSet.Builder()
                        .issuer(EMETTEUR)
                        .subject("citoyen-1")
                        .audience(client)
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plusSeconds(300)))
                        .claim("nonce", nonce)
                        .claim("name", "Awa Ouédraogo")
                        .claim("preferred_username", "awa")
                        .build());
    }

    private String signer(JWTClaimsSet revendications) {
        SignedJWT jeton =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cle.getKeyID()).build(),
                        revendications);
        try {
            jeton.sign(new RSASSASigner(cle));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jeton.serialize();
    }

    private void jetons(HttpExchange echange) throws IOException {
        observer(echange);
        Map<String, String> parametres = new LinkedHashMap<>();
        String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String paire : corps.split("&")) {
            String[] parties = paire.split("=", 2);
            String nom = URLDecoder.decode(parties[0], StandardCharsets.UTF_8);
            String valeur =
                    parties.length > 1 ? URLDecoder.decode(parties[1], StandardCharsets.UTF_8) : "";
            // Comme Keycloak : un paramètre répété rend la demande irrecevable.
            if (parametres.putIfAbsent(nom, valeur) != null) {
                byte[] erreur =
                        "{\"error\":\"invalid_request\",\"error_description\":\"duplicated parameter\"}"
                                .getBytes(StandardCharsets.UTF_8);
                echange.getResponseHeaders().set("Content-Type", "application/json");
                echange.sendResponseHeaders(400, erreur.length);
                try (var sortie = echange.getResponseBody()) {
                    sortie.write(erreur);
                }
                return;
            }
        }
        derniereDemande = parametres;
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("access_token", jetonAcces(dernierCertificat));
        reponse.put("token_type", "Bearer");
        reponse.put("expires_in", 300);
        reponse.put("refresh_token", "renouvellement");
        reponse.put("id_token", jetonIdentite(parametres.get("client_id")));
        reponse.put("scope", "openid");
        repondre(echange, JSONObjectUtils.toJSONString(reponse));
    }

    private void echo(HttpExchange echange) throws IOException {
        observer(echange);
        repondre(
                echange,
                JSONObjectUtils.toJSONString(
                        Map.of("membre", Certificats.uriSan(dernierCertificat).orElse(""))));
    }

    private void observer(HttpExchange echange) throws IOException {
        dernierCertificat =
                (X509Certificate)
                        ((HttpsExchange) echange).getSSLSession().getPeerCertificates()[0];
        dernierEnTeteAuthorization = echange.getRequestHeaders().getFirst("Authorization");
    }

    private static void repondre(HttpExchange echange, String json) throws IOException {
        byte[] corps = json.getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().add("Content-Type", "application/json");
        echange.sendResponseHeaders(200, corps.length);
        try (var sortie = echange.getResponseBody()) {
            sortie.write(corps);
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
