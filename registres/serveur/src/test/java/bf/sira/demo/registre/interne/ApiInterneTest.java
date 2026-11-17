package bf.sira.demo.registre.interne;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.demo.registre.RegistreDeTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class ApiInterneTest extends RegistreDeTest {

    @Test
    void rendLaFicheEntiereSansAuthentification() throws Exception {
        mvc.perform(get("/interne/personnes/" + AICHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiant_unique").value(AICHA))
                .andExpect(jsonPath("$.nom").value("KABORÉ"))
                .andExpect(jsonPath("$.prenoms").value("Wendkuni Aïcha"))
                .andExpect(jsonPath("$.date_naissance").isString())
                .andExpect(jsonPath("$.nom_mere").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {INCONNU, "abc", "99004821735", "990048217350'--", "9900482173500"})
    void repondIntrouvablePourUnePersonneAbsenteOuUnIdentifiantFantaisiste(String identifiant)
            throws Exception {
        mvc.perform(get("/interne/personnes/" + identifiant))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void produitLeDocumentEnPdf() throws Exception {
        byte[] pdf =
                mvc.perform(get("/interne/personnes/" + AICHA + "/documents/extrait-naissance"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isBetween(1_000, 100_000);
    }

    @Test
    void refuseUnTypeDeDocumentQueLeRegistreNeDelivrePas() throws Exception {
        mvc.perform(get("/interne/personnes/" + AICHA + "/documents/bulletin-3"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/interne/personnes/" + INCONNU + "/documents/extrait-naissance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void consigneChaqueAppelRecu() throws Exception {
        mvc.perform(get("/interne/personnes/" + AICHA));
        mvc.perform(get("/interne/personnes/" + INCONNU));
        mvc.perform(get("/interne/personnes/" + AICHA + "/documents/extrait-naissance"));

        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.elements[0].type").value("DOCUMENT"))
                .andExpect(jsonPath("$.elements[0].statut").value(200))
                .andExpect(jsonPath("$.elements[1].type").value("FICHE"))
                .andExpect(jsonPath("$.elements[1].statut").value(404))
                .andExpect(jsonPath("$.elements[2].mode").value("NORMAL"))
                .andExpect(jsonPath("$.elements[2].horodatage").isString());
    }
}
