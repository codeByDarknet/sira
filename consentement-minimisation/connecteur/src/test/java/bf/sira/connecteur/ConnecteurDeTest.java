package bf.sira.connecteur;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.TypesJose;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.openapi.ContratOpenApi;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.connecteur.FauxRegistre.Reponse;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le connecteur de l'état civil, complet : vraie connexion mTLS, vraie base, autorité de test, et
 * un faux registre existant derrière lui. La politique locale est l'exemple {@code etat-civil}.
 */
@SpringBootTest(
        classes = ConnecteurApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "sira.connecteur.registre.code=etat-civil",
            "sira.connecteur.registre.delai-lecture=500ms",
            // Le faux registre répond en http simple, sur la boucle locale.
            "sira.connecteur.registre.http-admis=true",
            "sira.connecteur.politique=src/test/resources/politiques/etat-civil.yaml",
            "management.server.port=0"
        })
abstract class ConnecteurDeTest {

    static final IdentiteMembre REGISTRE = IdentiteMembre.registre("etat-civil");
    static final IdentiteMembre SERVICE = IdentiteMembre.service("concours");
    static final String PSEUDONYME = "pseudo-concours-7Qm2";

    static final String ADULTE = "990000000001";
    static final String MAJEURE_AUJOURDHUI = "990000000002";
    static final String MAJEUR_DEMAIN = "990000000003";
    static final String INCONNU = "990000000404";
    static final String EN_PANNE = "990000000500";
    static final String LENT = "990000000900";
    static final String ILLISIBLE = "990000000800";
    static final String PAGE_HTML = "990000000700";

    /** Valeurs du registre qui ne doivent sortir que dans une attestation de niveau CHAMPS. */
    static final String NOM = "TRAORE-CONFIDENTIEL";

    static final String NOM_DU_PERE = "PERE-CONFIDENTIEL";
    static final String NAISSANCE = "1990-05-17";
    static final byte[] EXTRAIT = "%PDF-1.7 extrait fictif".getBytes(StandardCharsets.UTF_8);

    static final PkiDeTest PKI = PkiDeTest.creer();
    static final ContratOpenApi SPECIFICATION =
            ContratOpenApi.lire("consentement-minimisation/openapi.yaml");
    static final Signataire PASSERELLE = new Signataire(PKI.cachet(IdentiteMembre.PASSERELLE));
    static final Bicle CHIFFREMENT_DU_SERVICE = PKI.chiffrement(SERVICE);
    static final FauxRegistre REGISTRE_EXISTANT = registreExistant();
    static final PostgreSQLContainer BASE = new PostgreSQLContainer("postgres:17.11-alpine");

    private static final Path CLES = cles();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static {
        BASE.start();
    }

    @LocalServerPort private int port;
    @Autowired JdbcClient jdbc;

    private static Path cles() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-connecteur"), REGISTRE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FauxRegistre registreExistant() {
        LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Ouagadougou"));
        return new FauxRegistre()
                .repond(ADULTE, Reponse.json(fiche(NAISSANCE)))
                .repond(ADULTE + "/documents/extrait-naissance", Reponse.pdf(EXTRAIT))
                .repond(MAJEURE_AUJOURDHUI, Reponse.json(fiche(aujourdhui.minusYears(18))))
                .repond(MAJEUR_DEMAIN, Reponse.json(fiche(aujourdhui.minusYears(18).plusDays(1))))
                .repond(EN_PANNE, Reponse.erreur(500))
                .repond(LENT, Reponse.json(fiche(NAISSANCE)).apres(1500))
                .repond(ILLISIBLE, Reponse.json("{\"nom\": \"" + NOM))
                .repond(
                        PAGE_HTML,
                        new Reponse(200, "text/html", NOM.getBytes(StandardCharsets.UTF_8), 0));
    }

    private static String fiche(Object dateNaissance) {
        return """
        {"nom": "%s", "prenoms": "Awa Mariam", "date_naissance": "%s", "sexe": "F",
         "lieu_naissance": "Dédougou", "nom_pere": "%s", "date_deces": null}
        """
                .formatted(NOM, dateNaissance, NOM_DU_PERE);
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", CLES::toString);
        registre.add("sira.connecteur.registre.url", REGISTRE_EXISTANT::url);
        registre.add("spring.datasource.url", BASE::getJdbcUrl);
        registre.add("spring.datasource.username", BASE::getUsername);
        registre.add("spring.datasource.password", BASE::getPassword);
    }

    // --- Jeux demandés ----------------------------------------------------------------------

    static Map<String, Object> statut(String champ, String operateur, Object valeur) {
        Map<String, Object> regle = new HashMap<>();
        regle.put("champ", champ);
        regle.put("operateur", operateur);
        regle.put("valeur", valeur);
        return Map.of("code", "etat-civil.question", "niveau", "STATUT", "regle", regle);
    }

    static Map<String, Object> majeur() {
        return statut("date_naissance", "AGE_AU_MOINS", 18);
    }

    static Map<String, Object> champs(String... champs) {
        return Map.of("code", "etat-civil.identite", "niveau", "CHAMPS", "champs", List.of(champs));
    }

    static Map<String, Object> document(String type) {
        return Map.of("code", "etat-civil.extrait", "niveau", "DOCUMENT", "document", type);
    }

    static Map<String, Object> avec(Map<String, Object> base, String cle, Object valeur) {
        Map<String, Object> copie = new HashMap<>(base);
        copie.put(cle, valeur);
        return copie;
    }

    // --- Requêtes de la passerelle ----------------------------------------------------------

    /** Une requête telle que la passerelle la signe (contrat §8), pour le service « concours ». */
    static JWTClaimsSet.Builder requete(String identifiant, Map<String, Object> jeu) {
        Instant maintenant = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(IdentiteMembre.PASSERELLE.uri())
                .audience(REGISTRE.uri())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(maintenant))
                .expirationTime(Date.from(maintenant.plusSeconds(60)))
                .claim("sujet", Map.of("identifiant_unique", identifiant, "pseudonyme", PSEUDONYME))
                .claim("demandeur", demandeur(SERVICE, CHIFFREMENT_DU_SERVICE))
                .claim("demarche", "concours-direct")
                .claim("finalite", "Vérifier les conditions du concours.")
                .claim(
                        "autorisation",
                        Map.of(
                                "id", UUID.randomUUID().toString(),
                                "accordee_le", maintenant.toString(),
                                "canal", "CODE"))
                .claim("jeu", jeu);
    }

    static Map<String, Object> demandeur(IdentiteMembre membre, Bicle chiffrement) {
        return Map.of(
                "membre", membre.uri(),
                "certificat_chiffrement", Certificats.versPem(chiffrement.certificat()));
    }

    static String signer(JWTClaimsSet.Builder requete) {
        return PASSERELLE.signer(TypesJose.REQUETE, requete.build());
    }

    RestClient client(SslBundle bundle) {
        return new ClientsMtls(
                        bundle, Duration.ofSeconds(2), Duration.ofSeconds(5), RestClient::builder)
                .constructeur(REGISTRE)
                .baseUrl("https://localhost:" + port)
                .defaultStatusHandler(statut -> true, (requete, reponse) -> {})
                // Chaque réponse reçue est contrôlée contre la spécification de la couche.
                .requestInterceptor(ContratOpenApi.intercepteur(SPECIFICATION))
                .build();
    }

    ResponseEntity<String> envoyer(IdentiteMembre appelant, String jws) {
        return client(PKI.bundle(PKI.tls(appelant)))
                .post()
                .uri("/v1/requetes")
                .contentType(MediaType.parseMediaType("application/jose"))
                .body(jws)
                .retrieve()
                .toEntity(String.class);
    }

    ResponseEntity<String> envoyer(JWTClaimsSet.Builder requete) {
        return envoyer(IdentiteMembre.PASSERELLE, signer(requete));
    }

    // --- Lecture des réponses ---------------------------------------------------------------

    /** Ouvre l'attestation comme le fait le service : déchiffre, puis vérifie le cachet. */
    static JWTClaimsSet ouvrir(ResponseEntity<String> reponse) {
        Clock horloge = Clock.systemUTC();
        VerificateurJws verificateur =
                new VerificateurJws(PKI.autorite(), new RegistreJtiMemoire(10, horloge), horloge);
        return new LecteurAttestation(CHIFFREMENT_DU_SERVICE, verificateur)
                .ouvrir(JSON.readTree(reponse.getBody()).get("attestation").asString(), REGISTRE);
    }

    /** Ce que le journal local a retenu d'un échange. */
    String issueAuJournal(JWTClaimsSet.Builder requete) {
        return jdbc.sql("SELECT resultat FROM journal WHERE id_echange = :id")
                .param("id", requete.build().getJWTID())
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
