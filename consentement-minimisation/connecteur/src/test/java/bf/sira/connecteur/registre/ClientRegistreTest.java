package bf.sira.connecteur.registre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.connecteur.ProprietesConnecteur;
import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;

/**
 * Lecture d'un registre décrite par un fichier de raccordement : un registre « d'une autre équipe »
 * (noms anglais, enveloppe, clé d'API, identifiant en paramètre) est lu, traduit et réduit.
 */
class ClientRegistreTest {

    private static final String CONNU = "990000000001";
    private static final String CLE = "cle-de-test-0123456789abcdef";
    private static final String REPONSE =
            """
            {"data": {"citizen": {"nin": "990000000001", "surname": "SECRET-NOM",
              "birth": {"date": "2001-02-03"},
              "citizenships": [{"country": "BF"}]}}}
            """;

    @TempDir Path dossier;

    private HttpServer serveur;
    private final Map<String, String> recu = new ConcurrentHashMap<>();
    private volatile int statut = 200;
    private volatile String type = "application/json";
    private volatile String corps = REPONSE;

    @BeforeEach
    void demarrer() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext(
                "/",
                echange -> {
                    try (echange) {
                        recu.put("methode", echange.getRequestMethod());
                        recu.put("uri", echange.getRequestURI().toString());
                        recu.put(
                                "corps",
                                new String(
                                        echange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8));
                        echange.getRequestHeaders()
                                .forEach((nom, valeurs) ->
                                        recu.put(nom.toLowerCase(), valeurs.getFirst()));
                        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
                        echange.getResponseHeaders().add("Content-Type", type);
                        echange.sendResponseHeaders(
                                statut, octets.length == 0 ? -1 : octets.length);
                        echange.getResponseBody().write(octets);
                    }
                });
        serveur.start();
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
    }

    @Test
    void litTraduitEtReduitLaFicheDUnRegistreEtranger() throws IOException {
        ClientRegistre client = client(raccordement(etranger()), true, List.of("127.0.0.1"));

        JsonNode fiche = client.fiche(CONNU);

        assertThat(fiche.toString())
                .isEqualTo("{\"date_naissance\":\"2001-02-03\",\"nationalite\":[\"BF\"]}");
        assertThat(recu)
                .containsEntry("methode", "GET")
                .containsEntry("uri", "/registry/v2/citizens/lookup?nin=" + CONNU)
                .containsEntry("x-api-key", CLE);
    }

    @Test
    void unJetonPorteurEtUnePostSontPrisEnCharge() throws IOException {
        Files.writeString(dossier.resolve("jeton"), "jeton-porteur\n");
        String fichier =
                """
                adresse: http://127.0.0.1:%d
                fiche:
                  methode: POST
                  chemin: /recherche/{identifiant}
                  corps: '{"nin": "{identifiant}"}'
                  racine: data.citizen
                authentification: { type: JETON, fichier: %s }
                correspondance: { nom: surname }
                """
                        .formatted(port(), dossier.resolve("jeton"));

        JsonNode fiche = client(raccordement(fichier), true, List.of("127.0.0.1")).fiche(CONNU);

        assertThat(fiche.toString()).isEqualTo("{\"nom\":\"SECRET-NOM\"}");
        assertThat(recu)
                .containsEntry("methode", "POST")
                .containsEntry("corps", "{\"nin\": \"" + CONNU + "\"}")
                .containsEntry("authorization", "Bearer jeton-porteur");
    }

    @Test
    void uneEnveloppeVideOuUn404DisentPersonneInconnue() throws IOException {
        ClientRegistre client = client(raccordement(etranger()), true, List.of("127.0.0.1"));

        corps = "{\"data\": {\"citizen\": null}}";
        assertRefus(() -> client.fiche(CONNU), Motif.PERSONNE_INCONNUE);
        corps = "{\"data\": {}}";
        assertRefus(() -> client.fiche(CONNU), Motif.PERSONNE_INCONNUE);
        statut = 404;
        corps = "";
        assertRefus(() -> client.fiche(CONNU), Motif.PERSONNE_INCONNUE);
    }

    @Test
    void touteReponseInattendueDevientRegistreIndisponible() throws IOException {
        ClientRegistre client = client(raccordement(etranger()), true, List.of("127.0.0.1"));

        statut = 401;
        assertRefus(() -> client.fiche(CONNU), Motif.REGISTRE_INDISPONIBLE);
        statut = 200;
        corps = "{\"data\": {\"citizen\": [1, 2]}}";
        assertRefus(() -> client.fiche(CONNU), Motif.REGISTRE_INDISPONIBLE);
        corps = "pas du json";
        assertRefus(() -> client.fiche(CONNU), Motif.REGISTRE_INDISPONIBLE);
        corps = "{\"data\": \"" + "x".repeat(2048) + "\"}";
        assertRefus(() -> client.fiche(CONNU), Motif.REGISTRE_INDISPONIBLE);
        corps = REPONSE;
        type = "text/html";
        assertRefus(() -> client.fiche(CONNU), Motif.REGISTRE_INDISPONIBLE);
        // Aucun document n'est décrit : aucun n'est demandé.
        assertRefus(() -> client.document(CONNU, "extrait"), Motif.REGISTRE_INDISPONIBLE);
    }

    @Test
    void sansFichierLeConnecteurLitLaFicheInterneEntiere() {
        ClientRegistre client =
                new ClientRegistre(proprietes("http://127.0.0.1:" + port(), null, true, List.of()));

        assertThat(client.fiche(CONNU).path("data").path("citizen").path("surname").asString())
                .isEqualTo("SECRET-NOM");
        assertThat(recu).containsEntry("uri", "/interne/personnes/" + CONNU);
    }

    @Test
    void httpEstRefuseHorsDemonstration() throws IOException {
        Path fichier = raccordement(etranger());

        assertThatThrownBy(() -> client(fichier, false, List.of("127.0.0.1")))
                .hasMessageContaining("https");
        assertThatThrownBy(() ->
                        new ClientRegistre(
                                proprietes("http://registre:8080", null, false, List.of())))
                .hasMessageContaining("https");
    }

    @Test
    void unHoteInterneHorsDeLaListeEstRefuse() throws IOException {
        Path fichier = raccordement(etranger());

        assertThatThrownBy(() -> client(fichier, true, List.of()))
                .hasMessageContaining("adresse interne");
    }

    @Test
    void adresseEtFichierNeSeCumulentPas() throws IOException {
        Path fichier = raccordement(etranger());

        assertThatThrownBy(() ->
                        new ClientRegistre(
                                proprietes("http://registre:8080", fichier, true, List.of())))
                .hasMessageContaining("l'un des deux");
        assertThatThrownBy(() -> new ClientRegistre(proprietes(null, null, true, List.of())))
                .hasMessageContaining("l'un des deux");
    }

    @Test
    void unFichierMalFormeEmpecheLeDemarrage() throws IOException {
        for (String fichier :
                List.of(
                        etranger() + "inconnue: 1\n",
                        etranger().replace("http://127.0.0.1", "http://moi:secret@127.0.0.1"),
                        etranger()
                                .replace(
                                        "adresse: http://127.0.0.1:" + port(),
                                        "adresse: http://127.0.0.1:" + port() + "/chemin"),
                        etranger().replace("type: CLE_API", "type: MAISON"),
                        etranger().replace("{identifiant}", "{id}"),
                        etranger().replace("/birth/date", "birth..date"),
                        etranger().replace("date_naissance", "Date-Naissance"))) {
            Path chemin = raccordement(fichier);
            assertThatThrownBy(() -> client(chemin, true, List.of("127.0.0.1")))
                    .as(fichier)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void unSecretIllisibleOuSurPlusieursLignesEstRefuse() throws IOException {
        Files.writeString(dossier.resolve("cle"), "ligne1\nX-Injecte: oui");
        Path fichier = raccordement(etranger());

        assertThatThrownBy(() -> client(fichier, true, List.of("127.0.0.1")))
                .hasMessageContaining("secret");
    }

    private String etranger() throws IOException {
        Path cle = dossier.resolve("cle");
        if (!Files.exists(cle)) {
            Files.writeString(cle, CLE + "\n");
        }
        return """
        adresse: http://127.0.0.1:%d
        fiche:
          chemin: /registry/v2/citizens/lookup?nin={identifiant}
          racine: /data/citizen
        authentification:
          type: CLE_API
          en_tete: X-Api-Key
          fichier: %s
        correspondance:
          date_naissance: /birth/date
          nationalite: /citizenships/*/country
        """
                .formatted(port(), cle);
    }

    private Path raccordement(String contenu) throws IOException {
        Path fichier = Files.createTempFile(dossier, "raccordement", ".yaml");
        Files.writeString(fichier, contenu);
        return fichier;
    }

    private int port() {
        return serveur.getAddress().getPort();
    }

    private static ClientRegistre client(Path fichier, boolean httpAdmis, List<String> hotes) {
        return new ClientRegistre(proprietes(null, fichier, httpAdmis, hotes));
    }

    private static ProprietesConnecteur proprietes(
            String url, Path fichier, boolean httpAdmis, List<String> hotes) {
        return new ProprietesConnecteur(
                new ProprietesConnecteur.Registre(
                        "etranger",
                        url,
                        fichier,
                        httpAdmis,
                        hotes,
                        Duration.ofMillis(500),
                        Duration.ofSeconds(2),
                        DataSize.ofKilobytes(1),
                        DataSize.ofKilobytes(1)),
                Path.of("politique.yaml"),
                ZoneId.of("Africa/Ouagadougou"),
                ProprietesConnecteur.Appelant.PASSERELLE);
    }

    private static void assertRefus(Runnable appel, Motif motif) {
        assertThatThrownBy(appel::run)
                .isInstanceOfSatisfying(Refus.class, r -> assertThat(r.motif()).isEqualTo(motif));
    }
}
