package bf.sira.demo.registre.personnes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.demo.registre.RegistreDeTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PersonnesTest extends RegistreDeTest {

    private static final String FICHE = "/api/personnes/" + AICHA;

    @AfterEach
    void retablirLaFiche() throws Exception {
        corriger(FICHE, "{\"champs\":{\"lieu_naissance\":\"Koudougou\",\"nom\":\"KABORÉ\"}}")
                .andExpect(status().isOk());
    }

    @Test
    void listeLesPersonnesParPagesDansLOrdreDesNoms() throws Exception {
        mvc.perform(get("/api/personnes?taille=5").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(26))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.taille").value(5))
                .andExpect(jsonPath("$.elements.length()").value(5))
                .andExpect(jsonPath("$.elements[0].nom").value("BAMBARA"))
                .andExpect(jsonPath("$.elements[0].date_naissance").isString())
                .andExpect(jsonPath("$.elements[0].nom_mere").doesNotExist());
        mvc.perform(get("/api/personnes?taille=5&page=6").with(agentEtatCivil()))
                .andExpect(jsonPath("$.elements.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kabore", "  AÏCHA ", "990048217350", "Kaboré wend"})
    void trouveUnePersonneSansEgardAuxAccentsNiAuxMajuscules(String recherche) throws Exception {
        mvc.perform(get("/api/personnes").param("recherche", recherche).with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elements[0].identifiant").value(AICHA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "' OR 1=1 --", "\\"})
    void uneRechercheNEstJamaisUnMotifNiDuSql(String recherche) throws Exception {
        mvc.perform(get("/api/personnes").param("recherche", recherche).with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "taille=0", "taille=101", "page=abc", "page=100001"})
    void refuseUnePaginationHorsBornes(String parametres) throws Exception {
        mvc.perform(get("/api/personnes?" + parametres).with(agentEtatCivil()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void refuseUneRechercheTropLongue() throws Exception {
        mvc.perform(get("/api/personnes").param("recherche", "a".repeat(81)).with(agentEtatCivil()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rendLaFicheEtRepondIntrouvablePourUnInconnu() throws Exception {
        mvc.perform(get(adresse(FICHE)).with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiant").value(AICHA))
                .andExpect(jsonPath("$.fiche.nom_mere").isString())
                .andExpect(jsonPath("$.modifiee_le").isString());
        mvc.perform(get(adresse("/api/personnes/" + INCONNU)).with(agentEtatCivil()))
                .andExpect(status().isNotFound());
        corriger(adresse("/api/personnes/" + INCONNU), "{\"champs\":{\"lieu_naissance\":\"Kaya\"}}")
                .andExpect(status().isNotFound());
    }

    @Test
    void uneCorrectionChangeAussitotLaReponseDeLApiInterne() throws Exception {
        corriger(FICHE, "{\"champs\":{\"lieu_naissance\":\" Réo \",\"nom\":\"KABORE\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiche.lieu_naissance").value("Réo"));

        mvc.perform(get("/interne/personnes/" + AICHA))
                .andExpect(jsonPath("$.lieu_naissance").value("Réo"))
                .andExpect(jsonPath("$.nom").value("KABORE"))
                .andExpect(jsonPath("$.prenoms").value("Wendkuni Aïcha"));
        mvc.perform(get("/api/personnes").param("recherche", "kabore").with(agentEtatCivil()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"champs\":{}}",
                "{\"champs\":{\"identifiant_unique\":\"990000000001\"}}",
                "{\"champs\":{\"sexe\":\"M\"}}",
                "{\"champs\":{\"inconnu\":\"x\"}}",
                "{\"champs\":{\"lieu_naissance\":\"\"}}",
                "{\"champs\":{\"lieu_naissance\":\"<script>alert(1)</script>\"}}",
                "{\"champs\":{\"lieu_naissance\":{\"a\":1}}}",
                "{\"champs\":{\"date_naissance\":\"14/03/2003\"}}",
                "{\"champs\":{\"date_naissance\":\"2999-01-01\"}}",
                "{\"champs\":{\"lieu_naissance\":\"Kaya\",\"date_naissance\":\"hier\"}}"
            })
    void refuseUneCorrectionInvalideSansRienEcrire(String corps) throws Exception {
        corriger(FICHE, corps)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.erreurs[0].champ").isString())
                .andExpect(jsonPath("$.erreurs[0].message").isString());

        mvc.perform(get("/interne/personnes/" + AICHA))
                .andExpect(jsonPath("$.lieu_naissance").value("Koudougou"))
                .andExpect(jsonPath("$.date_naissance").value("2003-03-14"));
    }

    @Test
    void leMessageDeRefusNeReprendPasLaSaisie() throws Exception {
        String reponse =
                corriger(FICHE, "{\"champs\":{\"secret_xyz\":\"valeur_abc\"}}")
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(reponse).doesNotContain("secret_xyz").doesNotContain("valeur_abc");
    }

    @Test
    void lAgentObtientLeDocumentDepuisLeBackOfficeSansAppelerLApiInterne() throws Exception {
        mvc.perform(get(adresse(FICHE + "/documents/extrait-naissance")).with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get(adresse(FICHE + "/documents/bulletin-3")).with(agentEtatCivil()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/appels").with(agentEtatCivil()))
                .andExpect(jsonPath("$.total").value(0));
    }

    private ResultActions corriger(String chemin, String corps) throws Exception {
        return mvc.perform(
                patch(adresse(chemin))
                        .with(agentEtatCivil())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps));
    }
}
