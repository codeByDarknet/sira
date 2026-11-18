package bf.sira.demo.registre.contrat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.demo.registre.RegistreDeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/** Le registre d'état civil en marche répond réellement selon le contrat commun. */
class ApiContratTest extends RegistreDeTest {

    @Test
    void rendLaPersonneAuFormatDuContrat() throws Exception {
        String chemin = "/v1/etat-civil/personnes/" + AICHA;
        MockHttpServletResponse reponse = conforme(chemin, 200);

        assertThat(reponse.getContentAsString())
                .contains("\"identifiant\":\"" + AICHA + "\"")
                .doesNotContain("identifiant_unique", "date_naissance");
    }

    @Test
    void laSondeRepondSansToucherAuxDonnees() throws Exception {
        conforme("/v1/sante", 200);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/v1/etat-civil/personnes/" + INCONNU,
                "/v1/etat-civil/personnes/abc",
                "/v1/etat-civil/personnes/9900482173500",
                // Un registre ne sert que sa propre ressource.
                "/v1/casier/personnes/" + AICHA,
                "/v1/nationalite/personnes/" + AICHA
            })
    void repondIntrouvableAuFormatProbleme(String chemin) throws Exception {
        conforme(chemin, 404);
    }

    @Test
    void chaqueLectureEstConsigneeEtLaPanneSApplique() throws Exception {
        mvc.perform(get("/v1/etat-civil/personnes/" + AICHA)).andExpect(status().isOk());
        mvc.perform(get("/v1/sante")).andExpect(status().isOk());
        sql.sql("UPDATE panne SET mode = 'ERREURS'").update();
        mvc.perform(get("/v1/etat-civil/personnes/" + AICHA))
                .andExpect(status().is5xxServerError());

        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].mode").value("ERREURS"))
                .andExpect(jsonPath("$.elements[1].type").value("FICHE"))
                .andExpect(jsonPath("$.elements[1].statut").value(200));
    }

    private MockHttpServletResponse conforme(String chemin, int statut) throws Exception {
        MockHttpServletResponse reponse =
                mvc.perform(get(chemin)).andExpect(status().is(statut)).andReturn().getResponse();
        assertThat(
                        ContratCommunTest.SPEC.verifier(
                                "GET",
                                chemin,
                                statut,
                                reponse.getContentType(),
                                reponse.getContentAsByteArray()))
                .isTrue();
        return reponse;
    }
}
