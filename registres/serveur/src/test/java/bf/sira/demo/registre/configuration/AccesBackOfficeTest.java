package bf.sira.demo.registre.configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.agents.DepotRoles;
import bf.sira.demo.registre.RegistreDeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Le back-office n'est ouvert qu'aux agents de l'administration qui tient le registre. */
class AccesBackOfficeTest extends RegistreDeTest {

    private static final String CORRECTION = "{\"champs\":{\"lieu_naissance\":\"Koudougou\"}}";
    private static final String PANNE = "{\"mode\":\"ERREURS\"}";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/registre",
                "/api/personnes",
                "/api/personnes/" + AICHA,
                "/api/personnes/" + AICHA + "/documents/extrait-naissance",
                "/api/panne",
                "/api/appels",
                "/api/session"
            })
    void sansSessionToutEstRefuse(String chemin) throws Exception {
        mvc.perform(get(adresse(chemin))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/registre",
                "/api/personnes",
                "/api/personnes/" + AICHA,
                "/api/personnes/" + AICHA + "/documents/extrait-naissance",
                "/api/panne",
                "/api/appels"
            })
    void lAgentDUneAutreAdministrationEstRefuse(String chemin) throws Exception {
        mvc.perform(get(adresse(chemin)).with(agent("casier")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.nom").doesNotExist())
                .andExpect(jsonPath("$.elements").doesNotExist());
    }

    @Test
    void lAgentDUneAutreAdministrationNeModifieRien() throws Exception {
        mvc.perform(
                        patch(adresse("/api/personnes/" + AICHA))
                                .with(agent("casier"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CORRECTION))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/panne")
                                .with(agent("casier"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PANNE))
                .andExpect(status().isForbidden());

        mvc.perform(get("/interne/personnes/" + AICHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieu_naissance").value("Koudougou"));
    }

    @Test
    void uneSessionSansAdministrationEstRefusee() throws Exception {
        mvc.perform(get("/api/personnes").with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/personnes").with(agent("etat-civil-bis")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAgentDuRegistreEstAdmis() throws Exception {
        mvc.perform(get("/api/personnes").with(agentEtatCivil())).andExpect(status().isOk());
        mvc.perform(get("/api/registre").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("etat-civil"));
    }

    @Test
    void uneModificationSansJetonCsrfEstRefusee() throws Exception {
        mvc.perform(
                        patch(adresse("/api/personnes/" + AICHA))
                                .with(agentEtatCivil())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CORRECTION))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/panne")
                                .with(agentEtatCivil())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PANNE))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/panne").with(agentEtatCivil()))
                .andExpect(jsonPath("$.mode").value("NORMAL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/actuator/env", "/actuator/flyway", "/autre"})
    void rienDAutreNEstExpose(String chemin) throws Exception {
        mvc.perform(get(adresse(chemin)).with(agentEtatCivil())).andExpect(status().isForbidden());
    }

    @Autowired DepotRoles roles;

    /** Un agent de l'état civil, désigné par son identifiant de connexion. */
    private static RequestPostProcessor agentNomme(String identifiant) {
        return oidcLogin()
                .idToken(jeton ->
                        jeton.claim("administration", "etat-civil")
                                .claim("preferred_username", identifiant));
    }

    @Test
    void unAgentSansRoleNEntreNullePart() throws Exception {
        for (String chemin : new String[] {"/api/personnes", "/api/panne", "/api/utilisateurs"}) {
            mvc.perform(get(adresse(chemin)).with(agentNomme("sans.role")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/session").with(agentNomme("sans.role")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void unLecteurLitMaisNeModifieRien() throws Exception {
        roles.affecter("lecteur.test", "lecteur");
        mvc.perform(get("/api/personnes").with(agentNomme("lecteur.test")))
                .andExpect(status().isOk());
        mvc.perform(
                        patch(adresse("/api/personnes/" + AICHA))
                                .with(agentNomme("lecteur.test"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CORRECTION))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete(adresse("/api/personnes/" + AICHA))
                                .with(agentNomme("lecteur.test"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/panne")
                                .with(agentNomme("lecteur.test"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PANNE))
                .andExpect(status().isForbidden());
        // Le lecteur voit la liste des utilisateurs, mais n'en crée aucun.
        mvc.perform(
                        post("/api/utilisateurs")
                                .with(agentNomme("lecteur.test"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"prenom\":\"Awa\",\"nom\":\"OUEDRAOGO\","
                                                + "\"email\":\"awa@exemple.bf\",\"role\":\"agent\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/session").with(agentNomme("lecteur.test")))
                .andExpect(jsonPath("$.permissions[0]").value("APPELS_LIRE"));
    }

    @Test
    void lAdministrateurVoitLesRolesEtLesDroits() throws Exception {
        mvc.perform(get("/api/roles").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("administrateur"));
        mvc.perform(get("/api/permissions").with(agentEtatCivil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'PERSONNES_SUPPRIMER')]").exists());
    }
}
