package bf.sira.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.sira.commun.identite.IdentiteMembre;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ParcoursTest extends ServiceDeTest {

    private static final String IDENTIFIANT = "990048217350";
    private static final String PAR_CODE =
            "{\"canal\":\"CODE\",\"identifiant_unique\":\"" + IDENTIFIANT + "\"}";
    private static final String PAR_REDIRECTION =
            "{\"canal\":\"REDIRECTION\",\"identifiant_unique\":\"" + IDENTIFIANT + "\"}";
    private static final String CODE_RETOUR = "c0de-de-retour-du-navigateur";

    private final MockHttpSession navigateur = new MockHttpSession();

    @Test
    void parCodeLeDossierSeRemplitPuisSeCompleteQuandLeRegistreEnPanneRepond() throws Exception {
        mvc.perform(
                        post("/api/parcours/demandes")
                                .session(navigateur)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PAR_CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE_ACCORD"))
                .andExpect(jsonPath("$.code_verification").value("492113"))
                .andExpect(jsonPath("$.code_court").value("KLPM-4821"))
                .andExpect(jsonPath("$.url_accord").value(containsString("/accord/")))
                .andExpect(jsonPath("$.dossier_ouvert").value(false));
        FaussePasserelle.Recue creation = PASSERELLE.derniere();
        assertThat(creation.membre()).isEqualTo("urn:sira:service:concours");
        assertThat(creation.cleIdempotence()).isNotBlank();
        assertThat(creation.corps())
                .contains("\"demarche\":\"concours-direct\"", "\"canal\":\"CODE\"")
                .contains("\"identifiant_unique\":\"" + IDENTIFIANT + "\"")
                .contains("BEGIN CERTIFICATE");
        UUID demande = derniereDemande();

        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE_ACCORD"));
        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(status().isNotFound());

        repondre(demande, "PARTIELLE", statut(MAJEUR, true), extrait(), bac(), enAttente(CASIER));
        String avant = navigateur.getId();
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("PARTIELLE"))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));
        assertThat(navigateur.getId()).isNotEqualTo(avant);

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(containsString("CD-")))
                .andExpect(jsonPath("$.etat").value("EN_VERIFICATION"))
                .andExpect(jsonPath("$.pseudonyme").doesNotExist())
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='etat-civil.majeur')].valeur")
                                .value(contains(true)))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='casier.neant')].etat")
                                .value(contains("A_TRAITER")))
                .andExpect(
                        jsonPath(
                                        "$.verifications[?(@.jeu=='diplomes.bac')].champs.baccalaureat_serie")
                                .value(contains("D")))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='etat-civil.extrait')].piece.nom")
                                .value(contains("extrait-naissance.pdf")))
                .andExpect(
                        jsonPath("$.pieces_evitees")
                                .value(
                                        containsInAnyOrder(
                                                "Extrait d'acte de naissance",
                                                "Copie légalisée du diplôme")));
        mvc.perform(get("/api/parcours/dossier/pieces/etat-civil.extrait").session(navigateur))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(EXTRAIT));

        // Le casier répond enfin : c'est la relance du serveur qui complète le dossier.
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        relance.relancer();

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLET"))
                .andExpect(jsonPath("$.pieces_evitees.length()").value(3));
    }

    @Test
    void unNonOuUnRegistreQuiRefuseEnvoieLeDossierAUnAgentSansLeRejeter() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(
                demande,
                "COMPLETE",
                statut(MAJEUR, true),
                extrait(),
                sansReponse(BAC, "INCONNU"),
                statut(CASIER, false));

        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLETE"));

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(jsonPath("$.etat").value("A_EXAMINER"))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='casier.neant')].valeur")
                                .value(contains(false)))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='diplomes.bac')].etat")
                                .value(contains("INCONNU")));
    }

    @Test
    void parRedirectionLeRetourExigeLaDemandeDeLaSessionEtLeBonCode() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_REDIRECTION);
        assertThat(PASSERELLE.derniere().corps())
                .contains("\"canal\":\"REDIRECTION\"")
                .contains("\"retour_url\":\"http://concours.test/retour\"")
                .contains("\"identifiant_unique\":\"" + IDENTIFIANT + "\"");
        PASSERELLE.exigerCodeRetour(demande, CODE_RETOUR);
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));

        // Sans retour, l'écran ne peut rien obtenir : la passerelle n'est même pas interrogée.
        int appels = PASSERELLE.recues().size();
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE_ACCORD"));
        assertThat(PASSERELLE.recues()).hasSize(appels);

        mvc.perform(retour(navigateur, UUID.randomUUID(), CODE_RETOUR))
                .andExpect(status().isNotFound());
        mvc.perform(retour(navigateur, demande, "un-code-invente-de-toutes-pieces"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:code-invalide"));
        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(status().isNotFound());

        mvc.perform(retour(navigateur, demande, CODE_RETOUR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("COMPLETE"))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));
        assertThat(PASSERELLE.derniere().parametres()).isEqualTo("code_retour=" + CODE_RETOUR);
        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLET"));
    }

    @Test
    void leLienDeRetourDUnAutreNavigateurNeDonneAccesARien() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_REDIRECTION);
        PASSERELLE.exigerCodeRetour(demande, CODE_RETOUR);
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        MockHttpSession autre = new MockHttpSession();
        ouvrir(autre, PAR_REDIRECTION);
        int appels = PASSERELLE.recues().size();

        mvc.perform(retour(autre, demande, CODE_RETOUR)).andExpect(status().isNotFound());
        mvc.perform(retour(new MockHttpSession(), demande, CODE_RETOUR))
                .andExpect(status().isNotFound());

        assertThat(PASSERELLE.recues()).hasSize(appels);
    }

    @Test
    void leDossierDUneAutreSessionEstIntrouvable() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));
        MockHttpSession autre = new MockHttpSession();
        ouvrir(autre, PAR_CODE);

        mvc.perform(get("/api/parcours/dossier").session(autre)).andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/dossier/pieces/etat-civil.extrait").session(autre))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/dossier")).andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/demande")).andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/dossier/pieces/etat-civil.inconnu").session(navigateur))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneNouvelleDemarcheFaitOublierLeDossierDeLaPrecedente() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));

        ouvrir(navigateur, PAR_CODE);

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/dossier/pieces/etat-civil.extrait").session(navigateur))
                .andExpect(status().isNotFound());
    }

    @Test
    void parNotificationLIdentifiantUniquePartVersSiraEtNEstJamaisGarde() throws Exception {
        UUID demande =
                ouvrir(
                        navigateur,
                        "{\"canal\":\"NOTIFICATION\",\"identifiant_unique\":\"990048217350\"}");
        assertThat(PASSERELLE.derniere().corps())
                .contains("\"canal\":\"NOTIFICATION\"", "\"identifiant_unique\":\"990048217350\"");
        // La personne accepte dans son espace, avec le code de cet écran : le dossier s'ouvre ici.
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(
                        jsonPath("$.code_verification").value(FaussePasserelle.CODE_VERIFICATION))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));

        for (String table : new String[] {"demande", "dossier", "verification", "declaration"}) {
            assertThat(
                            sql.sql(
                                            "SELECT count(*) FROM %s t WHERE t::text LIKE '%%990048217350%%'"
                                                    .formatted(table))
                                    .query(Long.class)
                                    .single())
                    .isZero();
        }
        assertThat(navigateur.getAttributeNames().asIterator())
                .toIterable()
                .allSatisfy(nom ->
                        assertThat(String.valueOf(navigateur.getAttribute(nom)))
                                .doesNotContain("990048217350"));
    }

    @Test
    void unAccordDonneDOfficeNOuvrePasLeDossierAuPassant() throws Exception {
        // Le passant tape l'identifiant d'une personne qui a déjà donné accès à ce service.
        PASSERELLE.accepterDOffice();
        UUID demande =
                ouvrir(
                        navigateur,
                        "{\"canal\":\"NOTIFICATION\",\"identifiant_unique\":\"990048217350\"}");
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));

        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLETE"))
                .andExpect(jsonPath("$.dossier_ouvert").value(false));
        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/parcours/dossier/pieces/" + EXTRAIT_NAISSANCE).session(navigateur))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneSaisieInvalideEstRefuseeChampParChamp() throws Exception {
        for (String corps :
                new String[] {
                    "{\"canal\":\"NOTIFICATION\"}",
                    "{\"canal\":\"NOTIFICATION\",\"identifiant_unique\":\"99004821735\"}",
                    "{\"canal\":\"NOTIFICATION\",\"identifiant_unique\":\"99004821735a\"}",
                    "{\"canal\":\"CODE\"}",
                    "{\"canal\":\"REDIRECTION\",\"identifiant_unique\":\"99004821735\"}"
                }) {
            mvc.perform(creation(corps))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.erreurs[0].champ").value("identifiant_unique"))
                    .andExpect(content().string(not(containsString("99004821735"))));
        }
        mvc.perform(creation("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.erreurs[*].champ")
                                .value(containsInAnyOrder("canal", "identifiant_unique")));
        mvc.perform(creation("{\"canal\":\"TELEPATHIE\"}")).andExpect(status().isBadRequest());
        mvc.perform(
                        creation(
                                "{\"canal\":\"CODE\",\"identifiant_unique\":\"990048217350\",\"remplissage\":\""
                                        + "x".repeat(300_000)
                                        + "\"}"))
                .andExpect(status().isContentTooLarge());
        mvc.perform(
                        post("/api/parcours/retour")
                                .session(navigateur)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"demande\":\"pas-un-uuid\",\"code_retour\":\"x\"}"))
                .andExpect(status().isBadRequest());
        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void sansJetonCsrfRienNEstOuvert() throws Exception {
        mvc.perform(
                        post("/api/parcours/demandes")
                                .session(navigateur)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PAR_CODE))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/parcours").session(navigateur)).andExpect(status().isForbidden());
        assertThat(PASSERELLE.recues()).isEmpty();
    }

    @Test
    void unNavigateurNePeutPasOuvrirDesDemandesEnRafale() throws Exception {
        for (int i = 0; i < 5; i++) {
            ouvrir(navigateur, PAR_CODE);
        }

        mvc.perform(creation(PAR_CODE))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:trop-de-requetes"));

        assertThat(PASSERELLE.recues()).hasSize(5);
    }

    @Test
    void uneAttestationQuiNeConcernePasCeDossierEstEcartee() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        IdentiteMembre bourses = IdentiteMembre.service("bourses");
        repondre(
                demande,
                "COMPLETE",
                // D'un autre citoyen, bien que signée et chiffrée pour ce service.
                Map.of(
                        "jeu",
                        MAJEUR,
                        "etat",
                        "OBTENU",
                        "attestation",
                        attestation(SERVICE, "un-autre-citoyen", MAJEUR, "STATUT", vrai())),
                // D'un autre jeu que celui annoncé.
                Map.of(
                        "jeu",
                        CASIER,
                        "etat",
                        "OBTENU",
                        "attestation",
                        attestation(SERVICE, PSEUDONYME, "casier.autre", "STATUT", vrai())),
                // Chiffrée pour un autre service.
                Map.of(
                        "jeu",
                        BAC,
                        "etat",
                        "OBTENU",
                        "attestation",
                        attestation(
                                bourses, PSEUDONYME, BAC, "CHAMPS", Map.of("champs", Map.of()))),
                Map.of("jeu", EXTRAIT_NAISSANCE, "etat", "OBTENU", "attestation", "illisible"));

        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLETE"));

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(jsonPath("$.etat").value("A_EXAMINER"))
                .andExpect(
                        jsonPath("$.verifications[*].etat")
                                .value(contains("REFUSE", "REFUSE", "REFUSE", "REFUSE")))
                .andExpect(
                        jsonPath("$.verifications[*].valeur")
                                .value(contains(null, null, null, null)))
                .andExpect(jsonPath("$.pieces_evitees").isEmpty());
    }

    @Test
    void uneAttestationSigneeParUnAutreRegistreQueCeluiDuJeuEstEcartee() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        // Le registre du casier est un vrai membre, mais il ne détient pas « etat-civil.majeur ».
        repondre(
                demande,
                "PARTIELLE",
                Map.of(
                        "jeu",
                        MAJEUR,
                        "etat",
                        "OBTENU",
                        "attestation",
                        attestation(
                                IdentiteMembre.registre("casier"),
                                SERVICE,
                                PSEUDONYME,
                                MAJEUR,
                                "STATUT",
                                vrai())),
                enAttente(EXTRAIT_NAISSANCE),
                enAttente(BAC),
                enAttente(CASIER));

        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.etat").value("PARTIELLE"));

        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu == '" + MAJEUR + "')].etat")
                                .value(contains("REFUSE")))
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu == '" + MAJEUR + "')].valeur")
                                .value(contains((Object) null)));
    }

    @Test
    void quandSiraNeRepondPasLErreurResteSobre() throws Exception {
        PASSERELLE.enPanne(502);

        mvc.perform(creation(PAR_CODE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("panne"))));

        PASSERELLE.enPanne(404);
        mvc.perform(
                        creation(
                                "{\"canal\":\"NOTIFICATION\",\"identifiant_unique\":\"990000000000\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(not(containsString("990000000000"))));
    }

    @Test
    void abandonnerAnnuleLaDemandeChezSira() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);

        mvc.perform(delete("/api/parcours/demande").session(navigateur).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(PASSERELLE.derniere().methode()).isEqualTo("DELETE");
        assertThat(PASSERELLE.derniere().chemin()).endsWith(demande.toString());
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(status().isNotFound());
    }

    @Test
    void terminerFermeLaSessionDuNavigateur() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));

        mvc.perform(delete("/api/parcours").session(navigateur).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(navigateur.isInvalid()).isTrue();
    }

    private static Map<String, Object> vrai() {
        return Map.of("valeur", true);
    }

    private UUID derniereDemande() {
        return sql.sql("SELECT id FROM demande ORDER BY creee_le DESC LIMIT 1")
                .query(UUID.class)
                .single();
    }

    private MockHttpServletRequestBuilder creation(String corps) {
        return post("/api/parcours/demandes")
                .session(navigateur)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps);
    }

    private static MockHttpServletRequestBuilder retour(
            MockHttpSession session, UUID demande, String code) {
        return post("/api/parcours/retour")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"demande\":\"%s\",\"code_retour\":\"%s\"}".formatted(demande, code));
    }
}
