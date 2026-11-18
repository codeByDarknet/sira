package bf.sira.demo.registre.pannes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.demo.registre.RegistreDeTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PannesTest extends RegistreDeTest {

    private static final String FICHE = "/interne/personnes/" + AICHA;

    @Test
    void leRegistreRepondNormalementParDefaut() throws Exception {
        mvc.perform(get("/api/panne").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"))
                .andExpect(jsonPath("$.delai_ms").value(5000));
        mvc.perform(get(FICHE)).andExpect(status().isOk());
    }

    @Test
    void lentLeRegistreRepondApresLeDelaiChoisi() throws Exception {
        changer("{\"mode\":\"LENT\",\"delai_ms\":400}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LENT"))
                .andExpect(jsonPath("$.delai_ms").value(400));

        long debut = System.nanoTime();
        mvc.perform(get(FICHE)).andExpect(status().isOk());

        assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(ms(400));
        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(jsonPath("$.elements[0].mode").value("LENT"))
                .andExpect(jsonPath("$.elements[0].statut").value(200));
    }

    @Test
    void enErreurLeRegistreRepond500SansRienRendre() throws Exception {
        changer("{\"mode\":\"ERREURS\"}").andExpect(status().isOk());

        mvc.perform(get(FICHE))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.nom").doesNotExist());
        mvc.perform(get(FICHE + "/documents/extrait-naissance"))
                .andExpect(status().isInternalServerError());
        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].statut").value(500));
    }

    @Test
    void muetLeRegistreRetientLAppelPuisAbandonne() throws Exception {
        changer("{\"mode\":\"MUET\"}").andExpect(status().isOk());

        long debut = System.nanoTime();
        mvc.perform(get(FICHE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.nom").doesNotExist());

        assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(ms(300));
        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].mode").value("MUET"))
                .andExpect(jsonPath("$.elements[0].statut").doesNotExist());
    }

    @Test
    void leBackOfficeResteJoignablePendantUnePanne() throws Exception {
        changer("{\"mode\":\"ERREURS\"}").andExpect(status().isOk());

        mvc.perform(get(adresse("/api/personnes/" + AICHA)).with(agentEtatCivil()))
                .andExpect(status().isOk());
        changer("{\"mode\":\"NORMAL\"}").andExpect(status().isOk());
        mvc.perform(get(FICHE)).andExpect(status().isOk());
    }

    @Test
    void changerDeModeGardeLeDelaiPrecedent() throws Exception {
        changer("{\"mode\":\"LENT\",\"delai_ms\":250}").andExpect(status().isOk());

        changer("{\"mode\":\"NORMAL\"}").andExpect(jsonPath("$.delai_ms").value(250));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"mode\":\"EXPLOSE\"}",
                "{\"mode\":\"LENT\",\"delai_ms\":99}",
                "{\"mode\":\"LENT\",\"delai_ms\":30001}",
                "{\"mode\":\"LENT\",\"delai_ms\":\"vite\"}",
                "pas du json"
            })
    void refuseUnReglageInvalide(String corps) throws Exception {
        changer(corps).andExpect(status().isBadRequest());

        mvc.perform(get("/api/panne").with(agentEtatCivil()))
                .andExpect(jsonPath("$.mode").value("NORMAL"))
                .andExpect(jsonPath("$.delai_ms").value(5000));
    }

    private ResultActions changer(String corps) throws Exception {
        return mvc.perform(
                put("/api/panne")
                        .with(agentEtatCivil())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps));
    }

    private static Duration ms(long millisecondes) {
        return Duration.ofMillis(millisecondes);
    }
}
