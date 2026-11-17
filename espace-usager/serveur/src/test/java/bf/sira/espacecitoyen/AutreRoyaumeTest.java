package bf.sira.espacecitoyen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Le serveur n'attend que le royaume des citoyens. Ici le fournisseur signe, avec des clés que le
 * serveur sait vérifier, des jetons émis par un autre royaume : la connexion doit échouer.
 */
@SpringBootTest(
        properties = {
            "sira.oidc.emetteur=http://identite.test/realms/sira-citoyens",
            "sira.oidc.url-publique=" + Navigateur.URL_PUBLIQUE
        })
@AutoConfigureMockMvc
class AutreRoyaumeTest {

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur AUTRE_ROYAUME = new FauxFournisseur(PKI);
    private static final Path CLES = cles();

    @Autowired private MockMvc mvc;

    private static Path cles() {
        try {
            return PKI.ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.ESPACE_CITOYEN);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", CLES::toString);
        registre.add("sira.oidc.url-interne", AUTRE_ROYAUME::urlInterne);
    }

    @AfterAll
    static void arreter() {
        AUTRE_ROYAUME.close();
    }

    @Test
    void unJetonDUnAutreRoyaumeNOuvrePasDeSession() throws Exception {
        MockHttpSession session =
                new Navigateur(mvc, AUTRE_ROYAUME)
                        .connecter(Navigateur.URL_PUBLIQUE + "/?erreur=connexion");

        // Le code a bien été échangé : c'est le jeton reçu qui a été refusé.
        assertThat(AUTRE_ROYAUME.derniereDemande())
                .containsEntry("grant_type", "authorization_code");
        mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/demandes").session(session)).andExpect(status().isUnauthorized());
    }
}
