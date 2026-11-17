package bf.sira.demo.service;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DemarcheTest extends ServiceDeTest {

    @Test
    void laDemarcheEstPresenteeAvecLesPiecesQuElleEviteMemeQuandSiraNeRepondPlus()
            throws Exception {
        for (int statut : new int[] {0, 503}) {
            PASSERELLE.enPanne(statut);

            mvc.perform(get("/api/demarche"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.service.code").value("concours"))
                    .andExpect(jsonPath("$.service.nom").value("Concours directs 2026"))
                    .andExpect(jsonPath("$.code").value("concours-direct"))
                    .andExpect(jsonPath("$.libelle").value("Inscription à un concours direct"))
                    .andExpect(jsonPath("$.jeux.length()").value(4))
                    .andExpect(jsonPath("$.jeux[0].niveau").value("STATUT"))
                    .andExpect(jsonPath("$.jeux[0].registre").value("etat-civil"))
                    .andExpect(jsonPath("$.jeux[0].champs").isEmpty())
                    .andExpect(
                            jsonPath("$.declarations[*].code").value(contains("telephone", "cv")))
                    .andExpect(
                            jsonPath("$.declarations[*].nature").value(contains("TEXTE", "PIECE")))
                    .andExpect(jsonPath("$.declarations[1].libelle").value("Curriculum vitæ"))
                    .andExpect(
                            jsonPath("$.pieces")
                                    .value(
                                            contains(
                                                    "Extrait d'acte de naissance",
                                                    "Copie légalisée du diplôme",
                                                    "Extrait de casier judiciaire (bulletin n°3)")));
        }
    }

    @Test
    void lesRoutesNonPrevuesSontFermees() throws Exception {
        mvc.perform(get("/api/autre")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").with(agent("concours"))).andExpect(status().isOk());
    }
}
