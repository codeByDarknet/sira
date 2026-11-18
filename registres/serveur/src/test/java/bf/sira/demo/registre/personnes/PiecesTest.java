package bf.sira.demo.registre.personnes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.agents.DepotRoles;
import bf.sira.demo.registre.RegistreDeTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** Pièces déposées par un agent : vérifiées à leurs octets, et servies à la place du document. */
class PiecesTest extends RegistreDeTest {

    private static final byte[] PDF =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired DepotRoles roles;

    @Test
    void unPdfDeposeRemplaceLeDocumentProduit() throws Exception {
        String chemin = adresse("/api/personnes/" + AICHA) + "/pieces";
        mvc.perform(
                        multipart(chemin)
                                .file(
                                        new MockMultipartFile(
                                                "fichier", "acte.pdf", "application/pdf", PDF))
                                .param("type", "extrait-naissance")
                                .param("titre", "Extrait d’acte de naissance")
                                .param("champs", "nom", "prenoms")
                                .with(agentEtatCivil())
                                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].type").value("extrait-naissance"))
                .andExpect(jsonPath("$[0].champs[1]").value("prenoms"))
                .andExpect(jsonPath("$[0].type_mime").value("application/pdf"));

        byte[] servi =
                mvc.perform(get("/interne/personnes/" + AICHA + "/documents/extrait-naissance"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(servi).isEqualTo(PDF);
    }

    @Test
    void unFichierQuiNEstNiPdfNiImageEstRefuse() throws Exception {
        mvc.perform(
                        multipart(adresse("/api/personnes/" + AICHA) + "/pieces")
                                .file(
                                        new MockMultipartFile(
                                                "fichier",
                                                "faux.pdf",
                                                "application/pdf",
                                                "<script>alert(1)</script>".getBytes()))
                                .param("titre", "Pièce")
                                .with(agentEtatCivil())
                                .with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unLecteurNeDeposeRien() throws Exception {
        roles.affecter("lecteur.pieces", "lecteur");
        mvc.perform(
                        multipart(adresse("/api/personnes/" + AICHA) + "/pieces")
                                .file(
                                        new MockMultipartFile(
                                                "fichier", "a.pdf", "application/pdf", PDF))
                                .param("titre", "Pièce")
                                .with(
                                        oidcLogin()
                                                .idToken(jeton ->
                                                        jeton.claim("administration", "etat-civil")
                                                                .claim(
                                                                        "preferred_username",
                                                                        "lecteur.pieces")))
                                .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
