package bf.sira.demo.registre;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Le registre « état civil » en marche pour les tests : vraie base PostgreSQL jetable, vraies
 * données de démonstration, annuaire des agents simulé.
 */
@SpringBootTest(
        properties = {
            "sira.registre.dossier=../donnees/etat-civil",
            "sira.registre.duree-silence=300ms",
            "sira.oidc.emetteur=" + FauxFournisseur.EMETTEUR,
            "sira.oidc.url-publique=http://etat-civil.test/"
        })
@AutoConfigureMockMvc
public abstract class RegistreDeTest {

    /** Aïcha KABORÉ, premier persona de la démonstration. */
    protected static final String AICHA = "990048217350";

    protected static final String INCONNU = "990000000000";

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur ANNUAIRE = new FauxFournisseur(PKI);
    private static final Path REPERTOIRE_PKI = repertoirePki();

    @SuppressWarnings("resource") // arrêtée avec la JVM des tests, comme le veut Testcontainers
    private static final PostgreSQLContainer BASE =
            new PostgreSQLContainer("postgres:17.11-alpine");

    static {
        BASE.start();
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcClient sql;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", BASE::getJdbcUrl);
        registre.add("spring.datasource.username", BASE::getUsername);
        registre.add("spring.datasource.password", BASE::getPassword);
        registre.add("sira.pki.repertoire", REPERTOIRE_PKI::toString);
        registre.add("sira.oidc.url-interne", ANNUAIRE::urlInterne);
    }

    @BeforeEach
    void remettreEnEtat() {
        sql.sql("UPDATE panne SET mode = 'NORMAL', delai_ms = 5000").update();
        sql.sql("TRUNCATE appel_recu").update();
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

    protected static RequestPostProcessor agentEtatCivil() {
        return agent("etat-civil");
    }

    private static Path repertoirePki() {
        try {
            return PKI.ecrire(
                    Files.createTempDirectory("sira-pki"), IdentiteMembre.registre("etat-civil"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Le back-office désigne une fiche par son identifiant technique (UUID) : remplace
     * l'identifiant unique de la personne dans {@code /api/personnes/<identifiant>...}. Une
     * personne absente reçoit un UUID au hasard (fiche introuvable).
     */
    protected String adresse(String chemin) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^/api/personnes/([0-9]{12})(.*)$").matcher(chemin);
        if (!m.matches()) {
            return chemin;
        }
        String id =
                sql.sql("SELECT id::text FROM personne WHERE identifiant = ?")
                        .param(m.group(1))
                        .query(String.class)
                        .optional()
                        .orElse(java.util.UUID.randomUUID().toString());
        return "/api/personnes/" + id + m.group(2);
    }
}
