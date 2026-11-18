package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.openapi.ContratOpenApi;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.passerelle.demandes.CollectesApresAccord;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La passerelle complète : vraies connexions mTLS, vraie base PostgreSQL, autorité de test, deux
 * royaumes d'identité simulés et un connecteur simulé par registre. Le référentiel est celui de
 * l'exemple d'amorçage, importé au démarrage comme en exploitation.
 */
@SpringBootTest(
        classes = PasserelleApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "sira.mtls.mode=complet",
            "management.server.port=0",
            "sira.passerelle.espace-citoyen-url=https://citoyen.exemple.bf",
            "sira.passerelle.essai.personnes-fictives=" + PasserelleDeTest.PERSONNE_FICTIVE,
            // Une panne se constate et se répare en quelques secondes.
            "sira.passerelle.collecte.budget-immediat=3s",
            "sira.passerelle.collecte.attente-entre-tentatives=50ms",
            "sira.passerelle.disjoncteur.fenetre=4",
            "sira.passerelle.disjoncteur.appels-minimum=2",
            "sira.passerelle.disjoncteur.attente-ouvert=1s",
            "sira.passerelle.file.intervalle=200ms",
            "sira.passerelle.file.delai-initial=300ms",
            "sira.passerelle.file.delai-max=1s",
            // La purge et les points de contrôle sont déclenchés par les tests eux-mêmes.
            "sira.passerelle.purge.intervalle=1h",
            "sira.passerelle.journal.intervalle-points-de-controle=1h"
        })
public abstract class PasserelleDeTest {

    static final String PERSONNE_FICTIVE = "990000000001";

    static final IdentiteMembre CONCOURS = IdentiteMembre.service("concours");
    static final IdentiteMembre BOURSES = IdentiteMembre.service("bourses");
    static final String RETOUR_CONCOURS = "https://concours.exemple.bf/retour";

    static final PkiDeTest PKI = PkiDeTest.creer();
    static final Bicle CHIFFREMENT_CONCOURS = PKI.chiffrement(CONCOURS);
    static final Bicle CHIFFREMENT_BOURSES = PKI.chiffrement(BOURSES);
    static final FauxFournisseur ROYAUME_CITOYENS = new FauxFournisseur(PKI);
    static final FauxFournisseur ROYAUME_SOCLE = new FauxFournisseur(PKI);
    private static final Map<String, String> CODES_AFFICHES = new ConcurrentHashMap<>();

    static final FauxConnecteur ETAT_CIVIL = new FauxConnecteur(PKI, "etat-civil");
    static final FauxConnecteur NATIONALITE = new FauxConnecteur(PKI, "nationalite");
    static final FauxConnecteur CASIER = new FauxConnecteur(PKI, "casier");
    static final FauxConnecteur DIPLOMES = new FauxConnecteur(PKI, "diplomes");
    static final PostgreSQLContainer BASE = new PostgreSQLContainer("postgres:17.11-alpine");
    static final JsonMapper JSON = JsonMapper.builder().build();

    // Les quatre couches servies par ce processus, chacune décrite par son fichier.
    static final ContratOpenApi SPEC_PASSERELLE = ContratOpenApi.lire("passerelle/openapi.yaml");
    static final ContratOpenApi SPEC_CONSENTEMENT =
            ContratOpenApi.lire("consentement-minimisation/openapi.yaml");
    static final ContratOpenApi SPEC_JOURNAL = ContratOpenApi.lire("journal/openapi.yaml");
    static final ContratOpenApi SPEC_RESILIENCE = ContratOpenApi.lire("resilience/openapi.yaml");

    private static final Path TRAVAIL = preparer();
    private static final AtomicLong PERSONNES = new AtomicLong(990000100000L);
    private static final Map<String, RestClient> CLIENTS = new ConcurrentHashMap<>();
    private static final Bicle TLS_ESPACE_CITOYEN = PKI.tls(IdentiteMembre.ESPACE_CITOYEN);
    private static final Bicle TLS_CONSOLE = PKI.tls(IdentiteMembre.CONSOLE);

    static {
        BASE.start();
    }

    @LocalServerPort int port;
    @Autowired JdbcClient sql;
    @Autowired CollectesApresAccord collectesApresAccord;

    /** Clés de la passerelle, clé de pseudonymisation et amorçage : ce que monte son conteneur. */
    private static Path preparer() {
        try {
            Path travail = Files.createTempDirectory("sira-passerelle");
            PKI.ecrire(travail.resolve("pki"), IdentiteMembre.PASSERELLE);
            byte[] cle = new byte[32];
            new SecureRandom().nextBytes(cle);
            Files.write(travail.resolve("pseudonymisation.cle"), cle);
            String exemple;
            try (var flux = PasserelleDeTest.class.getResourceAsStream("/amorcage/exemple.yaml")) {
                exemple = new String(flux.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            // Seules les adresses changent : chaque registre est joint sur la boucle locale.
            exemple =
                    exemple.replace("https://connecteur-etat-civil:8443", ETAT_CIVIL.adresse())
                            .replace("https://connecteur-nationalite:8443", NATIONALITE.adresse())
                            .replace("https://connecteur-casier:8443", CASIER.adresse())
                            .replace("https://connecteur-diplomes:8443", DIPLOMES.adresse());
            Files.createDirectories(travail.resolve("amorcage"));
            Files.writeString(travail.resolve("amorcage/exemple.yaml"), exemple);
            return travail;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", () -> TRAVAIL.resolve("pki").toString());
        registre.add(
                "sira.passerelle.pseudonymisation-cle",
                () -> TRAVAIL.resolve("pseudonymisation.cle").toString());
        registre.add("sira.amorcage.repertoire", () -> TRAVAIL.resolve("amorcage").toString());
        registre.add("spring.datasource.url", BASE::getJdbcUrl);
        registre.add("spring.datasource.username", BASE::getUsername);
        registre.add("spring.datasource.password", BASE::getPassword);
        String cles = "/protocol/openid-connect/certs";
        registre.add("sira.passerelle.identite.citoyens.emetteur", () -> FauxFournisseur.EMETTEUR);
        registre.add(
                "sira.passerelle.identite.citoyens.cles",
                () -> ROYAUME_CITOYENS.urlInterne() + cles);
        registre.add("sira.passerelle.identite.socle.emetteur", () -> FauxFournisseur.EMETTEUR);
        registre.add(
                "sira.passerelle.identite.socle.cles", () -> ROYAUME_SOCLE.urlInterne() + cles);
    }

    @AfterEach
    void reparer() {
        List.of(ETAT_CIVIL, NATIONALITE, CASIER, DIPLOMES).forEach(c -> c.enPanne(false));
    }

    // --- Appelants --------------------------------------------------------------------------

    /** Une personne que personne d'autre n'utilise : les tests partagent la même base. */
    static String nouvellePersonne() {
        return String.valueOf(PERSONNES.incrementAndGet());
    }

    RestClient client(SslBundle bundle, String jeton) {
        RestClient.Builder constructeur =
                new ClientsMtls(
                                bundle,
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(20),
                                RestClient::builder)
                        .constructeur(IdentiteMembre.PASSERELLE)
                        .baseUrl("https://localhost:" + port)
                        .defaultStatusHandler(statut -> true, (requete, reponse) -> {})
                        // Chaque réponse reçue est contrôlée contre la spécification qui décrit
                        // sa route (voir ContratOpenApiTest).
                        .requestInterceptor(
                                ContratOpenApi.intercepteur(
                                        SPEC_PASSERELLE,
                                        SPEC_CONSENTEMENT,
                                        SPEC_JOURNAL,
                                        SPEC_RESILIENCE));
        if (jeton != null) {
            constructeur.defaultHeader("Authorization", "Bearer " + jeton);
        }
        return constructeur.build();
    }

    /** Un membre reconnu à son seul certificat. */
    RestClient membre(IdentiteMembre membre) {
        return CLIENTS.computeIfAbsent(
                membre.uri(), cle -> client(PKI.bundle(PKI.tls(membre)), null));
    }

    /** L'espace citoyen, pour le compte du citoyen connecté. */
    RestClient citoyen(String identifiantUnique) {
        return CLIENTS.computeIfAbsent(
                "citoyen:" + identifiantUnique,
                cle -> client(PKI.bundle(TLS_ESPACE_CITOYEN), jetonCitoyen(identifiantUnique)));
    }

    static String jetonCitoyen(String identifiantUnique) {
        return ROYAUME_CITOYENS.jetonAcces(
                TLS_ESPACE_CITOYEN.certificat(), r -> r.claim("iue", identifiantUnique));
    }

    static SslBundle espaceCitoyen() {
        return PKI.bundle(TLS_ESPACE_CITOYEN);
    }

    static SslBundle laConsole() {
        return PKI.bundle(TLS_CONSOLE);
    }

    static java.security.cert.X509Certificate certificatEspaceCitoyen() {
        return TLS_ESPACE_CITOYEN.certificat();
    }

    static java.security.cert.X509Certificate certificatConsole() {
        return TLS_CONSOLE.certificat();
    }

    /** La console, pour le compte d'un administrateur. */
    RestClient console(String compte, String... roles) {
        return CLIENTS.computeIfAbsent(
                "console:" + compte + List.of(roles),
                cle -> client(PKI.bundle(TLS_CONSOLE), jetonAdministrateur(compte, roles)));
    }

    static String jetonAdministrateur(String compte, String... roles) {
        return ROYAUME_SOCLE.jetonAcces(
                TLS_CONSOLE.certificat(),
                r ->
                        r.subject(compte)
                                .claim("name", compte)
                                .claim("realm_access", Map.of("roles", List.of(roles))));
    }

    // --- Requêtes ---------------------------------------------------------------------------

    static ResponseEntity<String> appeler(
            RestClient client, HttpMethod methode, String chemin, Object corps) {
        RestClient.RequestBodySpec requete = client.method(methode).uri(chemin);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(corps));
        }
        return requete.retrieve().toEntity(String.class);
    }

    static ResponseEntity<String> lire(RestClient client, String chemin) {
        return appeler(client, HttpMethod.GET, chemin, null);
    }

    static ResponseEntity<String> envoyer(RestClient client, String chemin, Object corps) {
        return appeler(client, HttpMethod.POST, chemin, corps);
    }

    static ResponseEntity<String> supprimer(RestClient client, String chemin) {
        return appeler(client, HttpMethod.DELETE, chemin, null);
    }

    static JsonNode json(ResponseEntity<String> reponse) {
        return JSON.readTree(reponse.getBody());
    }

    /** Le corps attendu d'une réponse réussie ; sinon le test échoue en montrant le refus. */
    static JsonNode reussi(ResponseEntity<String> reponse, int statut) {
        assertThat(reponse.getStatusCode().value()).as(reponse.getBody()).isEqualTo(statut);
        return reponse.getBody() == null ? null : json(reponse);
    }

    static void assertRefus(ResponseEntity<String> reponse, int statut, String code) {
        assertThat(reponse.getStatusCode().value()).as(reponse.getBody()).isEqualTo(statut);
        assertThat(reponse.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
        if (code != null) {
            assertThat(json(reponse).path("code").asString()).isEqualTo(code);
        }
    }

    // --- Parcours ---------------------------------------------------------------------------

    static Map<String, Object> demande(String demarche, String canal, Bicle chiffrement) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("demarche", demarche);
        corps.put("canal", canal);
        corps.put("certificat_chiffrement", Certificats.versPem(chiffrement.certificat()));
        return corps;
    }

    static Map<String, Object> avec(Map<String, Object> base, String cle, Object valeur) {
        Map<String, Object> copie = new HashMap<>(base);
        copie.put(cle, valeur);
        return copie;
    }

    ResponseEntity<String> creer(IdentiteMembre service, String cle, Map<String, Object> corps) {
        ResponseEntity<String> reponse =
                membre(service)
                        .post()
                        .uri("/v1/demandes")
                        .header("Idempotency-Key", cle)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(JSON.writeValueAsString(corps))
                        .retrieve()
                        .toEntity(String.class);
        if (reponse.getStatusCode().value() == 201) {
            JsonNode creee = json(reponse);
            if (creee.path("code_verification").isString()) {
                CODES_AFFICHES.put(
                        creee.path("id").asString(), creee.path("code_verification").asString());
            }
        }
        return reponse;
    }

    /**
     * Le code de vérification que l'écran du service a montré pour cette demande, comme le citoyen
     * le lit : la passerelle ne le rend qu'à la création.
     */
    static String codeAffiche(String demande) {
        return CODES_AFFICHES.get(demande);
    }

    /** Une demande créée par le service, clé d'idempotence neuve. */
    JsonNode creer(IdentiteMembre service, Map<String, Object> corps) {
        return reussi(creer(service, UUID.randomUUID().toString(), corps), 201);
    }

    /** Le concours demande l'accord par notification à cette personne. */
    JsonNode notifier(String identifiantUnique) {
        return creer(
                CONCOURS,
                avec(
                        demande("concours-direct", "NOTIFICATION", CHIFFREMENT_CONCOURS),
                        "identifiant_unique",
                        identifiantUnique));
    }

    /** Le citoyen décide ; pour accepter, il tape le code que montre l'écran du service. */
    JsonNode decider(String identifiantUnique, String demande, String decision) {
        return reussi(decision(identifiantUnique, demande, decision, codeAffiche(demande)), 200);
    }

    ResponseEntity<String> decision(
            String identifiantUnique, String demande, String decision, String code) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("decision", decision);
        corps.put("code_verification", code);
        ResponseEntity<String> reponse =
                envoyer(
                        citoyen(identifiantUnique),
                        "/v1/citoyen/demandes/" + demande + "/decision",
                        corps);
        // L'accord répond sans attendre les registres : la suite des tests lit la demande une fois
        // l'essai immédiat terminé, comme le service qui suit la collecte.
        attendre(Duration.ofSeconds(15), "collecte après accord", collectesApresAccord::auRepos);
        return reponse;
    }

    /** Une demande du concours, acceptée par la personne ; rend son identifiant. */
    String accordee(String identifiantUnique) {
        String id = notifier(identifiantUnique).path("id").asString();
        decider(identifiantUnique, id, "ACCEPTER");
        return id;
    }

    /** Le journal du citoyen, la ligne la plus récente d'abord. */
    JsonNode journalDe(String identifiantUnique) {
        return reussi(lire(citoyen(identifiantUnique), "/v1/citoyen/journal?taille=100"), 200)
                .path("elements");
    }

    static List<String> valeurs(JsonNode liste, String champ) {
        return liste.valueStream().map(e -> e.path(champ).asString()).toList();
    }

    static JsonNode element(JsonNode suivi, String jeu) {
        return suivi.path("elements")
                .valueStream()
                .filter(e -> jeu.equals(e.path("jeu").asString()))
                .findFirst()
                .orElseThrow();
    }

    JsonNode suivre(IdentiteMembre service, String demande) {
        return reussi(lire(membre(service), "/v1/demandes/" + demande), 200);
    }

    /**
     * Ouvre l'attestation d'un élément comme le fait le service : déchiffre, puis vérifie le cachet
     * du registre qui détient le jeu (le code d'un jeu commence par celui de son registre).
     */
    static JWTClaimsSet ouvrir(Bicle chiffrement, JsonNode element) {
        String jeu = element.path("jeu").asString();
        IdentiteMembre registre = IdentiteMembre.registre(jeu.substring(0, jeu.indexOf('.')));
        Clock horloge = Clock.systemUTC();
        return new LecteurAttestation(
                        chiffrement,
                        new VerificateurJws(
                                PKI.autorite(), new RegistreJtiMemoire(10, horloge), horloge))
                .ouvrir(element.path("attestation").asString(), registre);
    }

    static void attendre(Duration delai, String quoi, BooleanSupplier condition) {
        Instant limite = Instant.now().plus(delai);
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("en attente : " + quoi).isBefore(limite);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
