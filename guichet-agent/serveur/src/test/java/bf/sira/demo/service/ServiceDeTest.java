package bf.sira.demo.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.ScelleurAttestation;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.openapi.ContratOpenApi;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.demo.service.demandes.Relance;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * La plateforme « concours » en marche pour les tests : vraie base PostgreSQL jetable, passerelle
 * simulée jointe en mTLS avec de vraies clés, annuaire des agents simulé. La relance périodique est
 * déclenchée à la main. Chaque réponse de chaque test est confrontée à {@code
 * guichet-agent/openapi.yaml}.
 */
@SpringBootTest(
        properties = {
            "sira.service.relance=1h",
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=http://concours.test/"
        })
@ActiveProfiles("concours")
@AutoConfigureMockMvc
@Import(ServiceDeTest.ContratVerifie.class)
abstract class ServiceDeTest {

    protected static final ContratOpenApi SPECIFICATION =
            ContratOpenApi.lire("guichet-agent/openapi.yaml");

    protected static final IdentiteMembre SERVICE = IdentiteMembre.service("concours");
    protected static final String PSEUDONYME = "pseudonyme-d-aicha-chez-concours";
    protected static final byte[] EXTRAIT = "%PDF-1.7 extrait".getBytes(StandardCharsets.UTF_8);

    protected static final String MAJEUR = "etat-civil.majeur";
    protected static final String EXTRAIT_NAISSANCE = "etat-civil.extrait";
    protected static final String BAC = "diplomes.bac";
    protected static final String CASIER = "casier.neant";

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur ANNUAIRE = new FauxFournisseur(PKI);
    private static final Path REPERTOIRE_PKI = repertoirePki();
    protected static final FaussePasserelle PASSERELLE = new FaussePasserelle(PKI);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @SuppressWarnings("resource") // arrêtée avec la JVM des tests, comme le veut Testcontainers
    private static final PostgreSQLContainer BASE =
            new PostgreSQLContainer("postgres:17.11-alpine");

    static {
        BASE.start();
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcClient sql;
    @Autowired protected Relance relance;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", BASE::getJdbcUrl);
        registre.add("spring.datasource.username", BASE::getUsername);
        registre.add("spring.datasource.password", BASE::getPassword);
        registre.add("sira.pki.repertoire", REPERTOIRE_PKI::toString);
        registre.add("sira.oidc.url-interne", ANNUAIRE::urlInterne);
        registre.add("sira.passerelle.url", PASSERELLE::url);
    }

    @BeforeEach
    void remettreEnEtat() {
        PASSERELLE.reinitialiser();
        sql.sql("TRUNCATE declaration, verification, demande, dossier").update();
    }

    /** Un agent connecté, rattaché à l'administration donnée. */
    protected static RequestPostProcessor agent(String administration) {
        return oidcLogin()
                .idToken(jeton ->
                        jeton.claim("administration", administration)
                                .claim(
                                        "preferred_username",
                                        "agent." + administration + "@sira.example"));
    }

    /** Ouvre une demande depuis le navigateur donné et rend son identifiant. */
    protected UUID ouvrir(MockHttpSession navigateur, String corps) throws Exception {
        String reponse =
                mvc.perform(
                                post("/api/parcours/demandes")
                                        .session(navigateur)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corps))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(JsonPath.read(reponse, "$.id"));
    }

    /** La passerelle répondra que l'accord est donné, avec ces éléments. */
    protected static void repondre(UUID demande, String etat, Map<String, Object>... elements) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("id", demande.toString());
        corps.put("etat", etat);
        corps.put("pseudonyme", PSEUDONYME);
        corps.put("elements", List.of(elements));
        PASSERELLE.etat(demande, JSON.writeValueAsString(corps));
    }

    protected static Map<String, Object> enAttente(String jeu) {
        return Map.of("jeu", jeu, "etat", "A_TRAITER");
    }

    protected static Map<String, Object> sansReponse(String jeu, String etat) {
        return Map.of("jeu", jeu, "etat", etat);
    }

    protected static Map<String, Object> obtenu(String jeu, String niveau, Object resultat) {
        return Map.of(
                "jeu",
                jeu,
                "etat",
                "OBTENU",
                "attestation",
                attestation(SERVICE, PSEUDONYME, jeu, niveau, resultat));
    }

    protected static Map<String, Object> statut(String jeu, boolean valeur) {
        return obtenu(jeu, "STATUT", Map.of("valeur", valeur));
    }

    protected static Map<String, Object> bac() {
        return obtenu(
                BAC,
                "CHAMPS",
                Map.of("champs", Map.of("baccalaureat_serie", "D", "baccalaureat_annee", 2019)));
    }

    protected static Map<String, Object> extrait() {
        return obtenu(
                EXTRAIT_NAISSANCE,
                "DOCUMENT",
                Map.of(
                        "document",
                        Map.of(
                                "nom",
                                "extrait-naissance.pdf",
                                "type",
                                "application/pdf",
                                "sha256",
                                empreinte(EXTRAIT),
                                "contenu",
                                Base64.getEncoder().encodeToString(EXTRAIT))));
    }

    /** Une attestation signée par le registre du jeu et chiffrée pour le service donné. */
    protected static String attestation(
            IdentiteMembre destinataire,
            String pseudonyme,
            String jeu,
            String niveau,
            Object resultat) {
        return attestation(
                IdentiteMembre.registre(jeu.substring(0, jeu.indexOf('.'))),
                destinataire,
                pseudonyme,
                jeu,
                niveau,
                resultat);
    }

    /** Une attestation signée par le registre donné, qu'il détienne le jeu ou non. */
    protected static String attestation(
            IdentiteMembre registre,
            IdentiteMembre destinataire,
            String pseudonyme,
            String jeu,
            String niveau,
            Object resultat) {
        Instant maintenant = Instant.now();
        JWTClaimsSet revendications =
                new JWTClaimsSet.Builder()
                        .issuer(registre.uri())
                        .audience(destinataire.uri())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plusSeconds(3600)))
                        .subject(pseudonyme)
                        .claim("demarche", "concours-direct")
                        .claim("jeu", jeu)
                        .claim("niveau", niveau)
                        .claim("resultat", resultat)
                        .build();
        return new ScelleurAttestation(
                        new Signataire(PKI.cachet(registre)), PKI.autorite(), Clock.systemUTC())
                .sceller(revendications, certificatChiffrement(destinataire), destinataire);
    }

    /**
     * Le certificat du service est celui de son répertoire de clés ; un autre service a le sien.
     */
    private static String certificatChiffrement(IdentiteMembre destinataire) {
        try {
            return destinataire.equals(SERVICE)
                    ? Files.readString(REPERTOIRE_PKI.resolve("chiffrement.crt"))
                    : Certificats.versPem(PKI.chiffrement(destinataire).certificat());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path repertoirePki() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), SERVICE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Toute réponse d'une route du fichier doit y être déclarée et conforme à son schéma. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ContratVerifie {

        @Bean
        MockMvcBuilderCustomizer contratOpenApi() {
            return constructeur -> constructeur.alwaysDo(ContratOpenApi.controle(SPECIFICATION));
        }
    }
}
