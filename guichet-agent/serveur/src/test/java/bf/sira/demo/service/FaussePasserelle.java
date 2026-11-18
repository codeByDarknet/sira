package bf.sira.demo.service;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLParameters;

/**
 * Passerelle simulée : exige le mTLS comme la vraie, retient ce qu'elle reçoit, et tient l'état des
 * demandes que le test fait évoluer (accord, réponses des registres, panne).
 */
final class FaussePasserelle {

    static final String CODE_VERIFICATION = "492113";
    static final String CODE_COURT = "KLPM-4821";
    static final String DEMARCHE =
            """
            {"code":"concours-direct","libelle":"Inscription à un concours direct","jeux":[
             {"code":"etat-civil.majeur","libelle":"Vous êtes majeur","niveau":"STATUT","registre":"etat-civil"},
             {"code":"etat-civil.extrait","libelle":"Votre extrait d'acte de naissance","niveau":"DOCUMENT","registre":"etat-civil"},
             {"code":"diplomes.bac","libelle":"Votre baccalauréat","niveau":"CHAMPS","registre":"diplomes"},
             {"code":"casier.neant","libelle":"Votre casier judiciaire est vierge","niveau":"STATUT","registre":"casier"}]}
            """;

    record Recue(
            String methode,
            String chemin,
            String parametres,
            String cleIdempotence,
            String corps,
            String membre) {}

    private final HttpsServer serveur;
    private final List<Recue> recues = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> etats = new ConcurrentHashMap<>();
    private final Map<UUID, String> codesRetour = new ConcurrentHashMap<>();
    private volatile int panne;
    private volatile boolean dOffice;

    FaussePasserelle(PkiDeTest pki) {
        try {
            serveur =
                    HttpsServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.setHttpsConfigurator(
                new HttpsConfigurator(pki.contexteTls(pki.tls(IdentiteMembre.PASSERELLE))) {
                    @Override
                    public void configure(HttpsParameters parametres) {
                        SSLParameters tls = getSSLContext().getDefaultSSLParameters();
                        tls.setNeedClientAuth(true);
                        parametres.setSSLParameters(tls);
                    }
                });
        serveur.createContext("/", this::traiter);
        serveur.start();
    }

    String url() {
        return "https://localhost:" + serveur.getAddress().getPort();
    }

    void reinitialiser() {
        recues.clear();
        etats.clear();
        codesRetour.clear();
        panne = 0;
        dOffice = false;
    }

    /** Les demandes créées désormais sont acceptées d'office, sans code ni écran d'accord. */
    void accepterDOffice() {
        dOffice = true;
    }

    /** Toute requête reçoit désormais ce statut d'erreur (0 : retour à la normale). */
    void enPanne(int statut) {
        panne = statut;
    }

    /** Ce que répondra désormais la lecture de la demande. */
    void etat(UUID demande, String json) {
        etats.put(demande, json);
    }

    /** La demande ne se lit plus qu'avec ce code de retour, comme par le canal REDIRECTION. */
    void exigerCodeRetour(UUID demande, String code) {
        codesRetour.put(demande, code);
    }

    List<Recue> recues() {
        return recues;
    }

    Recue derniere() {
        return recues.getLast();
    }

    private void traiter(HttpExchange echange) throws IOException {
        X509Certificate client =
                (X509Certificate)
                        ((HttpsExchange) echange).getSSLSession().getPeerCertificates()[0];
        String membre = Certificats.uriSan(client).orElse("");
        String chemin = echange.getRequestURI().getRawPath();
        String parametres = echange.getRequestURI().getRawQuery();
        recues.add(
                new Recue(
                        echange.getRequestMethod(),
                        chemin,
                        parametres,
                        echange.getRequestHeaders().getFirst("Idempotency-Key"),
                        new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        membre));
        if (panne != 0) {
            repondre(echange, panne, "<html>panne</html>");
        } else if (!membre.startsWith("urn:sira:service:")) {
            repondre(echange, 403, probleme(403));
        } else if (chemin.equals("/v1/demandes")) {
            repondre(echange, 201, creer());
        } else if (chemin.equals("/v1/demarches/concours-direct")) {
            repondre(echange, 200, DEMARCHE);
        } else if (chemin.startsWith("/v1/demandes/")) {
            demande(echange, UUID.fromString(chemin.substring("/v1/demandes/".length())));
        } else {
            repondre(echange, 404, probleme(404));
        }
    }

    private String creer() {
        UUID id = UUID.randomUUID();
        if (dOffice) {
            etats.put(id, "{\"id\":\"%s\",\"etat\":\"EN_COURS\"}".formatted(id));
            return "{\"id\":\"%s\",\"etat\":\"EN_COURS\",\"expire_le\":\"%s\",\"jeux\":[]}"
                    .formatted(id, Instant.now().plusSeconds(300));
        }
        etats.put(id, "{\"id\":\"%s\",\"etat\":\"EN_ATTENTE_ACCORD\"}".formatted(id));
        return """
        {"id":"%s","etat":"EN_ATTENTE_ACCORD","url_accord":"http://citoyen.test/accord/%s",
         "code_court":"%s","code_verification":"%s","expire_le":"%s","jeux":%s}
        """
                .formatted(
                        id,
                        id,
                        CODE_COURT,
                        CODE_VERIFICATION,
                        Instant.now().plusSeconds(300),
                        DEMARCHE.substring(DEMARCHE.indexOf('['), DEMARCHE.lastIndexOf(']') + 1));
    }

    private void demande(HttpExchange echange, UUID id) throws IOException {
        String etat = etats.get(id);
        String attendu = codesRetour.get(id);
        String parametres = echange.getRequestURI().getRawQuery();
        String presente =
                parametres != null && parametres.startsWith("code_retour=")
                        ? URLDecoder.decode(
                                parametres.substring("code_retour=".length()),
                                StandardCharsets.UTF_8)
                        : null;
        if (etat == null) {
            repondre(echange, 404, probleme(404));
        } else if (attendu != null && !attendu.equals(presente)) {
            repondre(echange, 403, probleme(403));
        } else if (echange.getRequestMethod().equals("DELETE")) {
            etats.put(id, "{\"id\":\"%s\",\"etat\":\"ANNULEE\"}".formatted(id));
            repondre(echange, 204, "");
        } else {
            repondre(echange, 200, etat);
        }
    }

    private static String probleme(int statut) {
        return "{\"type\":\"urn:sira:erreur:refus\",\"title\":\"Refus\",\"status\":%d}"
                .formatted(statut);
    }

    private static void repondre(HttpExchange echange, int statut, String corps)
            throws IOException {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders()
                .add(
                        "Content-Type",
                        statut < 400 ? "application/json" : "application/problem+json");
        echange.sendResponseHeaders(statut, octets.length == 0 ? -1 : octets.length);
        try (var sortie = echange.getResponseBody()) {
            sortie.write(octets);
        }
    }
}
