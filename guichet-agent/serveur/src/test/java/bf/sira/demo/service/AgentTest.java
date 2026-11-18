package bf.sira.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AgentTest extends ServiceDeTest {

    private static final String DOSSIERS = "/api/agent/dossiers";

    @Test
    void sansSessionOuDUneAutreAdministrationLeBackOfficeEstFerme() throws Exception {
        String dossier = DOSSIERS + "/" + dossierIncomplet();

        mvc.perform(get(DOSSIERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(dossier)).andExpect(status().isUnauthorized());
        mvc.perform(get(DOSSIERS).with(agent("bourses"))).andExpect(status().isForbidden());
        mvc.perform(get(dossier).with(agent("bourses"))).andExpect(status().isForbidden());
        mvc.perform(get(dossier + "/pieces/etat-civil.extrait").with(agent("bourses")))
                .andExpect(status().isForbidden());
        mvc.perform(get(DOSSIERS).with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(post(dossier + "/verifications").with(agent("bourses")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(dossier + "/verifications").with(agent("concours")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lAgentVoitLesDossiersEtSeulementCeQueLeServiceARecu() throws Exception {
        UUID dossier = dossierIncomplet();

        mvc.perform(get(DOSSIERS).with(agent("concours")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.taille").value(20))
                .andExpect(jsonPath("$.elements[0].id").value(dossier.toString()))
                .andExpect(jsonPath("$.elements[0].etat").value("EN_VERIFICATION"))
                .andExpect(content().string(not(containsString(PSEUDONYME))));
        mvc.perform(get(DOSSIERS + "?etat=COMPLET").with(agent("concours")))
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(get(DOSSIERS + "/" + dossier).with(agent("concours")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossier.numero").value(containsString("CD-")))
                .andExpect(jsonPath("$.dossier.verifications.length()").value(4))
                .andExpect(jsonPath("$.demandes[0].origine").value("CITOYEN"))
                .andExpect(jsonPath("$.demandes[0].canal").value("CODE"))
                .andExpect(content().string(not(containsString(PSEUDONYME))));
        mvc.perform(
                        get(DOSSIERS + "/" + dossier + "/pieces/etat-civil.extrait")
                                .with(agent("concours")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(EXTRAIT));
    }

    @Test
    void uneRechercheHorsBornesOuUnDossierInconnuSontRefuses() throws Exception {
        mvc.perform(get(DOSSIERS + "?taille=101").with(agent("concours")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(DOSSIERS + "?page=0").with(agent("concours")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(DOSSIERS + "?etat=PERDU").with(agent("concours")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(DOSSIERS + "/pas-un-uuid").with(agent("concours")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(DOSSIERS + "/" + UUID.randomUUID()).with(agent("concours")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(DOSSIERS + "/" + UUID.randomUUID() + "/verifications")
                                .with(agent("concours"))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void redemanderUneVerificationPasseParLeCitoyenEtCompleteLeDossier() throws Exception {
        UUID dossier = dossierIncomplet();
        String verifications = DOSSIERS + "/" + dossier + "/verifications";

        String reponse =
                mvc.perform(post(verifications).with(agent("concours")).with(csrf()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.origine").value("AGENT"))
                        .andExpect(jsonPath("$.canal").value("NOTIFICATION"))
                        .andExpect(jsonPath("$.etat").value("EN_ATTENTE_ACCORD"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID demande = UUID.fromString(JsonPath.read(reponse, "$.id"));
        assertThat(PASSERELLE.derniere().corps())
                .contains("\"canal\":\"NOTIFICATION\"", "\"pseudonyme\":\"" + PSEUDONYME + "\"")
                .doesNotContain("identifiant_unique\":\"");

        // Tant que le citoyen n'a pas répondu, on ne lui envoie pas une seconde demande.
        mvc.perform(post(verifications).with(agent("concours")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:demande-deja-envoyee"));

        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        relance.relancer();

        mvc.perform(get(DOSSIERS + "/" + dossier).with(agent("concours")))
                .andExpect(jsonPath("$.dossier.etat").value("COMPLET"))
                .andExpect(jsonPath("$.demandes[*].origine").value(contains("AGENT", "CITOYEN")));
    }

    @Test
    void uneReponseQuiDesigneUnAutreCitoyenNeToucheAucunDossier() throws Exception {
        UUID dossier = dossierIncomplet();
        String reponse =
                mvc.perform(
                                post(DOSSIERS + "/" + dossier + "/verifications")
                                        .with(agent("concours"))
                                        .with(csrf()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID demande = UUID.fromString(JsonPath.read(reponse, "$.id"));
        PASSERELLE.etat(
                demande,
                "{\"id\":\"%s\",\"etat\":\"COMPLETE\",\"pseudonyme\":\"un-autre-citoyen\",\"elements\":[]}"
                        .formatted(demande));

        relance.relancer();

        assertThat(sql.sql("SELECT count(*) FROM dossier").query(Long.class).single()).isOne();
        mvc.perform(get(DOSSIERS + "/" + dossier).with(agent("concours")))
                .andExpect(jsonPath("$.dossier.etat").value("EN_VERIFICATION"));
    }

    /** Un dossier ouvert par un citoyen, auquel il manque la réponse du casier. */
    private UUID dossierIncomplet() throws Exception {
        MockHttpSession navigateur = new MockHttpSession();
        UUID demande =
                ouvrir(navigateur, "{\"canal\":\"CODE\",\"identifiant_unique\":\"990048217350\"}");
        repondre(demande, "PARTIELLE", statut(MAJEUR, true), extrait(), bac(), enAttente(CASIER));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));
        PASSERELLE.reinitialiser();
        PASSERELLE.etat(demande, "{\"id\":\"%s\",\"etat\":\"PARTIELLE\"}".formatted(demande));
        return sql.sql("SELECT id FROM dossier").query(UUID.class).single();
    }
}
