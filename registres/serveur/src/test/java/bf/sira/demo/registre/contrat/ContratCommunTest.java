package bf.sira.demo.registre.contrat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.openapi.ContratOpenApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le contrat commun {@code registres/openapi.yaml} et son implémentation ne divergent pas : mêmes
 * routes, et chaque personne des trois registres, présentée au format du contrat, est conforme au
 * schéma publié (validation par json-schema-validator, dialecte OpenAPI 3.1).
 */
class ContratCommunTest {

    static final ContratOpenApi SPEC = ContratOpenApi.lire("registres/openapi.yaml");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void lesRoutesDeclareesSontCellesDuControleur() {
        assertThat(SPEC.routes())
                .isEqualTo(ContratOpenApi.routesDesControleurs("bf.sira.demo.registre.contrat"));
        assertThat(SPEC.document().path("openapi").asString()).isEqualTo("3.1.0");
    }

    @Test
    void lesExemplesRespectentLeursSchemas() {
        assertThat(SPEC.validerExemples()).isGreaterThanOrEqualTo(SPEC.routes().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"etat-civil", "nationalite", "casier"})
    void chaquePersonneDuJeuDeDonneesEstConformeAuContrat(String registre) {
        List<Map<String, Object>> personnes = personnes(registre);

        assertThat(personnes).hasSizeGreaterThanOrEqualTo(20);
        for (Map<String, Object> fiche : personnes) {
            assertThat(conforme(registre, ContratCommun.projeter(registre, fiche).orElseThrow()))
                    .isTrue();
        }
    }

    @Test
    void unCasierViergeRendUneListeVideEtUneMentionUneCondamnation() {
        List<Map<String, Object>> casiers = personnes("casier");

        for (Map<String, Object> fiche : casiers) {
            List<?> condamnations = (List<?>) ContratCommun.casier(fiche).get("condamnations");
            assertThat(condamnations).hasSize("NEANT".equals(fiche.get("bulletin3")) ? 0 : 1);
        }
        assertThat(casiers).anyMatch(fiche -> "MENTIONS".equals(fiche.get("bulletin3")));
    }

    @Test
    void lesRegistresHorsContratNExposentRien() {
        assertThat(ContratCommun.projeter("diplomes", Map.of("nom", "X"))).isEmpty();
    }

    @Test
    void laValidationRepereLesEcartsAuLieuDeToutAccepter() {
        Map<String, Object> conforme = ContratCommun.etatCivil(personnes("etat-civil").getFirst());

        assertThat(conforme("etat-civil", conforme)).isTrue();
        for (Map<String, Object> ecart :
                List.of(
                        sans(conforme, "naissance"),
                        avec(conforme, "nom_mere", "X"),
                        avec(conforme, "sexe", "X"),
                        avec(conforme, "identifiant", "12"))) {
            assertThatThrownBy(() -> conforme("etat-civil", ecart))
                    .isInstanceOf(AssertionError.class);
        }
    }

    /**
     * Les fichiers de raccordement des connecteurs de démonstration lisent la route du contrat, et
     * chaque emplacement de leur correspondance existe dans la réponse d'une personne du jeu.
     */
    @ParameterizedTest
    @ValueSource(strings = {"etat-civil", "nationalite", "casier"})
    @SuppressWarnings("unchecked")
    void lesRaccordementsDeDemonstrationLisentLeContrat(String registre) throws Exception {
        Map<String, Object> raccordement;
        try (var lecteur =
                java.nio.file.Files.newBufferedReader(
                        Path.of("../raccordements", registre + ".yaml"))) {
            raccordement =
                    new org.yaml.snakeyaml.Yaml(
                                    new org.yaml.snakeyaml.constructor.SafeConstructor(
                                            new org.yaml.snakeyaml.LoaderOptions()))
                            .load(lecteur);
        }
        String chemin = (String) ((Map<String, Object>) raccordement.get("fiche")).get("chemin");
        assertThat(SPEC.routes()).contains("GET " + chemin);

        Map<String, Object> reponse =
                ContratCommun.projeter(registre, personnes(registre).getFirst()).orElseThrow();
        Map<String, String> correspondance =
                (Map<String, String>) raccordement.get("correspondance");
        assertThat(correspondance).isNotEmpty();
        correspondance.forEach((nom, pointeur) ->
                assertThat(present(reponse, pointeur.substring(1).split("/")))
                        .as("%s : %s", registre, pointeur)
                        .isTrue());
    }

    /**
     * L'emplacement existe-t-il ? « * » parcourt les éléments d'une liste, un nombre en désigne un
     * (comme le connecteur : {@code /nationalites/0/code}).
     */
    private static boolean present(Object noeud, String[] segments) {
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].equals("*") && noeud instanceof List<?> liste) {
                String[] suite = java.util.Arrays.copyOfRange(segments, i + 1, segments.length);
                return liste.stream().anyMatch(element -> present(element, suite));
            }
            if (segments[i].matches("[0-9]+") && noeud instanceof List<?> liste) {
                int rang = Integer.parseInt(segments[i]);
                if (rang >= liste.size()) {
                    return false;
                }
                noeud = liste.get(rang);
                continue;
            }
            if (!(noeud instanceof Map<?, ?> objet) || !objet.containsKey(segments[i])) {
                return false;
            }
            noeud = objet.get(segments[i]);
        }
        return true;
    }

    /** Contrôle {@code fiche} comme réponse 200 de la lecture d'une personne de ce registre. */
    static boolean conforme(String registre, Map<String, Object> fiche) {
        return SPEC.verifier(
                "GET",
                "/v1/" + registre + "/personnes/990048217350",
                200,
                "application/json",
                JSON.writeValueAsString(fiche).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> sans(Map<String, Object> objet, String champ) {
        Map<String, Object> copie = new LinkedHashMap<>(objet);
        copie.remove(champ);
        return copie;
    }

    private static Map<String, Object> avec(Map<String, Object> objet, String champ, Object v) {
        Map<String, Object> copie = new LinkedHashMap<>(objet);
        copie.put(champ, v);
        return copie;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> personnes(String registre) {
        Map<String, Object> fichier =
                JSON.readValue(
                        Path.of("../donnees", registre, "personnes.json").toFile(), Map.class);
        return (List<Map<String, Object>>) fichier.get("personnes");
    }
}
