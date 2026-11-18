package bf.sira.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Le formulaire du service : ce que la personne déclare à côté de ce que les registres attestent,
 * puis le dépôt du dossier.
 */
class DeclarationsTest extends ServiceDeTest {

    private static final String DECLARATIONS = "/api/parcours/dossier/declarations/";
    private static final byte[] CV = "%PDF-1.7 curriculum".getBytes(StandardCharsets.UTF_8);
    private static final String TELEPHONE = "+226 70 00 00 00";

    private final MockHttpSession navigateur = new MockHttpSession();

    @Test
    void laPersonneCompleteLeFormulaireConsulteSesPiecesPuisDepose() throws Exception {
        UUID demande = dossierOuvert();

        mvc.perform(saisie(navigateur, "telephone", "{\"valeur\":\"  " + TELEPHONE + " \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declarations[0].code").value("telephone"))
                .andExpect(jsonPath("$.declarations[0].nature").value("TEXTE"))
                .andExpect(jsonPath("$.declarations[0].valeur").value(TELEPHONE))
                .andExpect(jsonPath("$.declarations[0].remplace").value(false));
        mvc.perform(piece(navigateur, "cv", CV).param("nom", "../../Mon CV <final>.PDF"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.declarations[?(@.code=='cv')].piece.nom")
                                .value(contains("Mon CV _final_.pdf")))
                .andExpect(
                        jsonPath("$.declarations[?(@.code=='cv')].piece.taille")
                                .value(contains(CV.length)))
                .andExpect(
                        jsonPath("$.declarations[?(@.code=='cv')].libelle")
                                .value(contains("Curriculum vitæ")));

        mvc.perform(get(DECLARATIONS + "cv/piece").session(navigateur))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(CV));
        mvc.perform(get(DECLARATIONS + "cv/piece?apercu=true").session(navigateur))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        mvc.perform(
                        get("/api/parcours/dossier/pieces/" + EXTRAIT_NAISSANCE + "?apercu=true")
                                .session(navigateur))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(EXTRAIT));
        mvc.perform(get(DECLARATIONS + "telephone/piece").session(navigateur))
                .andExpect(status().isNotFound());

        // Le casier n'a pas encore répondu : le dossier est déposé quand même.
        mvc.perform(post("/api/parcours/dossier/depot").session(navigateur).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depose_le").isNotEmpty())
                .andExpect(jsonPath("$.etat").value("EN_VERIFICATION"))
                .andExpect(jsonPath("$.declarations.length()").value(2));

        // Après le dépôt, ce que la personne a déclaré ne change plus.
        mvc.perform(saisie(navigateur, "telephone", "{\"valeur\":\"70 11 11 11\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:dossier-depose"));
        mvc.perform(piece(navigateur, "cv", CV))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:sira:erreur:dossier-depose"));
        mvc.perform(delete(DECLARATIONS + "cv").session(navigateur).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/parcours/dossier/depot").session(navigateur).with(csrf()))
                .andExpect(status().isConflict());

        // Le registre en panne répond enfin : le dossier déposé se complète.
        repondre(demande, "COMPLETE", statut(MAJEUR, true), extrait(), bac(), statut(CASIER, true));
        relance.relancer();
        mvc.perform(get("/api/parcours/dossier").session(navigateur))
                .andExpect(jsonPath("$.etat").value("COMPLET"))
                .andExpect(
                        jsonPath("$.declarations[?(@.code=='telephone')].valeur")
                                .value(contains(TELEPHONE)));
    }

    @Test
    void retirerUneDeclarationLEfface() throws Exception {
        dossierOuvert();
        mvc.perform(piece(navigateur, "cv", CV)).andExpect(status().isOk());

        mvc.perform(delete(DECLARATIONS + "cv").session(navigateur).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declarations").isEmpty());
        mvc.perform(delete(DECLARATIONS + "cv").session(navigateur).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get(DECLARATIONS + "cv/piece").session(navigateur))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneInformationDUnRegistreNeSeDeclareJamais() throws Exception {
        dossierOuvert();

        // Reçue (oui/non, champs, pièce) ou encore attendue : refusée, en saisie comme en pièce.
        for (String jeu : new String[] {MAJEUR, BAC, EXTRAIT_NAISSANCE, CASIER}) {
            mvc.perform(saisie(navigateur, jeu, "{\"valeur\":\"oui\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("urn:sira:erreur:information-du-registre"));
            mvc.perform(piece(navigateur, jeu, CV))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("urn:sira:erreur:information-du-registre"));
            mvc.perform(delete(DECLARATIONS + jeu).session(navigateur).with(csrf()))
                    .andExpect(status().isConflict());
        }
        mvc.perform(saisie(navigateur, "inconnu", "{\"valeur\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(piece(navigateur, "inconnu", CV)).andExpect(status().isNotFound());
        assertThat(declarations()).isZero();
    }

    @Test
    void uneInformationQueLeRegistreNAPasTransmiseSeRemplaceParUnePiece() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(
                demande,
                "COMPLETE",
                statut(MAJEUR, true),
                extrait(),
                sansReponse(BAC, "INCONNU"),
                statut(CASIER, true));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));

        mvc.perform(saisie(navigateur, BAC, "{\"valeur\":\"D 2019\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("valeur"));
        mvc.perform(piece(navigateur, BAC, CV).param("nom", "releve-bac.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declarations[0].code").value(BAC))
                .andExpect(jsonPath("$.declarations[0].remplace").value(true))
                .andExpect(jsonPath("$.declarations[0].libelle").value("Votre baccalauréat"))
                .andExpect(jsonPath("$.declarations[0].piece.nom").value("releve-bac.pdf"))
                // La condition reste celle du registre : sans réponse.
                .andExpect(
                        jsonPath("$.verifications[?(@.jeu=='diplomes.bac')].etat")
                                .value(contains("INCONNU")));

        UUID dossier = sql.sql("SELECT id FROM dossier").query(UUID.class).single();
        mvc.perform(get("/api/agent/dossiers/" + dossier).with(agent("concours")))
                .andExpect(jsonPath("$.dossier.declarations[0].remplace").value(true))
                .andExpect(content().string(not(containsString(PSEUDONYME))))
                .andExpect(content().string(not(containsString(IDENTIFIANT))));
        mvc.perform(
                        get("/api/agent/dossiers/" + dossier + "/declarations/" + BAC + "/piece")
                                .with(agent("concours")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(CV));
        mvc.perform(
                        get("/api/agent/dossiers/" + dossier + "/declarations/" + BAC + "/piece")
                                .with(agent("bourses")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/agent/dossiers/" + dossier + "/declarations/cv/piece")
                                .with(agent("concours")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/agent/dossiers/" + UUID.randomUUID() + "/declarations/cv/piece")
                                .with(agent("concours")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneSaisieOuUnePieceNonConformeEstRefusee() throws Exception {
        dossierOuvert();

        for (String corps :
                new String[] {
                    "{\"valeur\":\"   \"}",
                    "{}",
                    "{\"valeur\":\"" + "7".repeat(121) + "\"}",
                    "{\"valeur\":\"70\\u000000\"}",
                    "{\"valeur\":\"" + "7".repeat(1001) + "\"}"
                }) {
            mvc.perform(saisie(navigateur, "telephone", corps))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        mvc.perform(saisie(navigateur, "cv", "{\"valeur\":\"mon cv\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("valeur"));
        mvc.perform(piece(navigateur, "telephone", CV))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("piece"));

        // Un fichier qui n'est pas un PDF, même annoncé comme tel.
        mvc.perform(piece(navigateur, "cv", "<html><script>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("piece"));
        mvc.perform(piece(navigateur, "cv", new byte[0])).andExpect(status().isBadRequest());
        mvc.perform(
                        put(DECLARATIONS + "cv/piece")
                                .session(navigateur)
                                .with(csrf())
                                .contentType(MediaType.TEXT_HTML)
                                .content(CV))
                .andExpect(status().isUnsupportedMediaType());

        // Plus de 2 Mo.
        byte[] lourd = Arrays.copyOf(CV, 2 * 1024 * 1024 + 1);
        mvc.perform(piece(navigateur, "cv", lourd)).andExpect(status().isContentTooLarge());
        // Les autres routes gardent la borne habituelle de 256 Kio.
        mvc.perform(
                        saisie(
                                navigateur,
                                "telephone",
                                "{\"valeur\":\"x\",\"r\":\"" + "x".repeat(300 * 1024) + "\"}"))
                .andExpect(status().isContentTooLarge());
        mvc.perform(
                        post("/api/parcours/dossier/depot")
                                .session(navigateur)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_PDF)
                                .content(Arrays.copyOf(CV, 300 * 1024)))
                .andExpect(status().isContentTooLarge());
        // Le code est borné : rien qui ressemble à un chemin.
        mvc.perform(saisie(navigateur, "CV", "{\"valeur\":\"x\"}"))
                .andExpect(status().isBadRequest());

        assertThat(declarations()).isZero();
    }

    @Test
    void uneAutreSessionOuUneRequeteSansJetonCsrfNeTouchentARien() throws Exception {
        dossierOuvert();
        mvc.perform(piece(navigateur, "cv", CV)).andExpect(status().isOk());
        MockHttpSession autre = new MockHttpSession();
        ouvrir(autre, PAR_CODE);

        mvc.perform(saisie(autre, "telephone", "{\"valeur\":\"70\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(piece(autre, "cv", CV)).andExpect(status().isNotFound());
        mvc.perform(get(DECLARATIONS + "cv/piece").session(autre)).andExpect(status().isNotFound());
        mvc.perform(delete(DECLARATIONS + "cv").session(autre).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/parcours/dossier/depot").session(autre).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(DECLARATIONS + "telephone")
                                .session(navigateur)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valeur\":\"70\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/parcours/dossier/depot").session(navigateur))
                .andExpect(status().isForbidden());

        assertThat(declarations()).isOne();
        assertThat(
                        sql.sql("SELECT count(*) FROM dossier WHERE depose_le IS NOT NULL")
                                .query(Long.class)
                                .single())
                .isZero();
    }

    private static final String IDENTIFIANT = "990048217350";
    private static final String PAR_CODE =
            "{\"canal\":\"CODE\",\"identifiant_unique\":\"" + IDENTIFIANT + "\"}";

    /** Un dossier ouvert dans ce navigateur, auquel il manque la réponse du casier. */
    private UUID dossierOuvert() throws Exception {
        UUID demande = ouvrir(navigateur, PAR_CODE);
        repondre(demande, "PARTIELLE", statut(MAJEUR, true), extrait(), bac(), enAttente(CASIER));
        mvc.perform(get("/api/parcours/demande").session(navigateur))
                .andExpect(jsonPath("$.dossier_ouvert").value(true));
        return demande;
    }

    private long declarations() {
        return sql.sql("SELECT count(*) FROM declaration").query(Long.class).single();
    }

    private static MockHttpServletRequestBuilder saisie(
            MockHttpSession session, String code, String corps) {
        return put(DECLARATIONS + code)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps);
    }

    private static MockHttpServletRequestBuilder piece(
            MockHttpSession session, String code, byte[] contenu) {
        return put(DECLARATIONS + code + "/piece")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_PDF)
                .content(contenu);
    }
}
