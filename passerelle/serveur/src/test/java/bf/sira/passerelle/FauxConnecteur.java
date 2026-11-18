package bf.sira.passerelle;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Attentes;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.ScelleurAttestation;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLParameters;

/**
 * Un connecteur simulé, fidèle au contrat §8 : mTLS, requête signée vérifiée (cachet de la
 * passerelle, destinataire, validité, rejeu), attestation signée puis chiffrée pour le demandeur.
 * Il retient ce qu'il a reçu et peut tomber en panne.
 */
final class FauxConnecteur implements AutoCloseable {

    private final IdentiteMembre registre;
    private final HttpsServer serveur;
    private final VerificateurJws verificateur;
    private final ScelleurAttestation scelleur;
    private final List<JWTClaimsSet> recues = new CopyOnWriteArrayList<>();
    private final List<String> appelants = new CopyOnWriteArrayList<>();
    private final Set<String> inconnus = ConcurrentHashMap.newKeySet();
    private final Set<String> refuses = ConcurrentHashMap.newKeySet();
    private final Map<String, Runnable> pendantLaRequete = new ConcurrentHashMap<>();
    private volatile boolean enPanne;

    FauxConnecteur(PkiDeTest pki, String code) {
        this.registre = IdentiteMembre.registre(code);
        Clock horloge = Clock.systemUTC();
        this.verificateur =
                new VerificateurJws(
                        pki.autorite(), new RegistreJtiMemoire(10_000, horloge), horloge);
        this.scelleur =
                new ScelleurAttestation(
                        new Signataire(pki.cachet(registre)), pki.autorite(), horloge);
        try {
            serveur =
                    HttpsServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.setHttpsConfigurator(
                new HttpsConfigurator(pki.contexteTls(pki.tls(registre))) {
                    @Override
                    public void configure(HttpsParameters parametres) {
                        SSLParameters tls = getSSLContext().getDefaultSSLParameters();
                        tls.setNeedClientAuth(true);
                        parametres.setSSLParameters(tls);
                    }
                });
        serveur.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        serveur.createContext("/v1/requetes", this::repondre);
        serveur.start();
    }

    String adresse() {
        return "https://localhost:" + serveur.getAddress().getPort();
    }

    /** En panne, le connecteur répond 503 à tout. */
    void enPanne(boolean enPanne) {
        this.enPanne = enPanne;
    }

    /** Le registre ne connaît pas cette personne (404). */
    void ignore(String identifiantUnique) {
        inconnus.add(identifiantUnique);
    }

    /** Le registre refuse les questions sur cette personne (422). */
    void refuse(String identifiantUnique) {
        refuses.add(identifiantUnique);
    }

    /**
     * À la prochaine requête pour cette personne, le registre fait l'action, puis répond par une
     * panne (503) : de quoi faire arriver un événement pendant la collecte.
     */
    void pendantLaRequete(String identifiantUnique, Runnable action) {
        pendantLaRequete.put(identifiantUnique, action);
    }

    /** Les requêtes valides reçues pour cette personne. */
    List<JWTClaimsSet> recues(String identifiantUnique) {
        return recues.stream()
                .filter(r -> identifiantUnique.equals(sujet(r).get("identifiant_unique")))
                .toList();
    }

    /** Identités des certificats clients qui ont appelé. */
    List<String> appelants() {
        return appelants;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sujet(JWTClaimsSet requete) {
        return (Map<String, Object>) requete.getClaim("sujet");
    }

    @SuppressWarnings("unchecked")
    private void repondre(HttpExchange echange) throws IOException {
        X509Certificate client =
                (X509Certificate)
                        ((HttpsExchange) echange).getSSLSession().getPeerCertificates()[0];
        appelants.add(Certificats.uriSan(client).orElse(""));
        String jws = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (enPanne) {
            envoyer(echange, 503, Map.of());
            return;
        }
        JWTClaimsSet requete;
        try {
            requete = verificateur.verifier(jws, Attentes.requete(registre));
        } catch (JoseRefuse e) {
            envoyer(echange, 403, Map.of());
            return;
        }
        String identifiant = (String) sujet(requete).get("identifiant_unique");
        if (inconnus.contains(identifiant)) {
            envoyer(echange, 404, Map.of());
            return;
        }
        if (refuses.contains(identifiant)) {
            envoyer(echange, 422, Map.of());
            return;
        }
        recues.add(requete);
        Runnable action = pendantLaRequete.remove(identifiant);
        if (action != null) {
            action.run();
            envoyer(echange, 503, Map.of());
            return;
        }
        Map<String, Object> demandeur = (Map<String, Object>) requete.getClaim("demandeur");
        Map<String, Object> jeu = (Map<String, Object>) requete.getClaim("jeu");
        IdentiteMembre destinataire =
                IdentiteMembre.lire((String) demandeur.get("membre")).orElseThrow();
        Instant maintenant = Instant.now();
        JWTClaimsSet attestation =
                new JWTClaimsSet.Builder()
                        .issuer(registre.uri())
                        .audience(destinataire.uri())
                        .jwtID(requete.getJWTID())
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plusSeconds(3600)))
                        .subject((String) sujet(requete).get("pseudonyme"))
                        .claim("demarche", requete.getClaim("demarche"))
                        .claim("jeu", jeu.get("code"))
                        .claim("niveau", jeu.get("niveau"))
                        .claim("resultat", resultat(jeu))
                        .build();
        try {
            envoyer(
                    echange,
                    200,
                    Map.of(
                            "attestation",
                            scelleur.sceller(
                                    attestation,
                                    (String) demandeur.get("certificat_chiffrement"),
                                    destinataire)));
        } catch (JoseRefuse e) {
            envoyer(echange, 403, Map.of());
        }
    }

    private static Map<String, Object> resultat(Map<String, Object> jeu) {
        return switch ((String) jeu.get("niveau")) {
            case "CHAMPS" -> Map.of("champs", Map.of("nom", "SAWADOGO"));
            case "DOCUMENT" ->
                    Map.of(
                            "document",
                            Map.of(
                                    "nom",
                                    "piece.pdf",
                                    "type",
                                    "application/pdf",
                                    "contenu",
                                    "JVBERg=="));
            default -> Map.of("valeur", true);
        };
    }

    private static void envoyer(HttpExchange echange, int statut, Map<String, Object> corps)
            throws IOException {
        byte[] octets = JSONObjectUtils.toJSONString(corps).getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().add("Content-Type", "application/json");
        echange.sendResponseHeaders(statut, octets.length);
        try (var sortie = echange.getResponseBody()) {
            sortie.write(octets);
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
