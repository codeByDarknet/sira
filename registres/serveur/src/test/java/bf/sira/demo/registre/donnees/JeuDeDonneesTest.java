package bf.sira.demo.registre.donnees;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Le jeu de données fictives tient debout : mêmes personnes, mêmes identités, partout. */
class JeuDeDonneesTest {

    private static final Path DONNEES = Path.of("../donnees");
    private static final Path CITOYENS = Path.of("../../identite/demo/sira-citoyens-users-0.json");
    private static final List<String> REGISTRES =
            List.of("etat-civil", "nationalite", "casier", "diplomes");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"etat-civil", "nationalite", "casier", "diplomes"})
    void chaqueFicheRespecteLaDescriptionDeSonRegistre(String registre) throws Exception {
        PropertySource<?> description =
                new YamlPropertySourceLoader()
                        .load(
                                registre,
                                new FileSystemResource(
                                        DONNEES.resolve(registre + "/registre.yaml")))
                        .getFirst();
        assertThat(description.getProperty("sira.registre.code")).isEqualTo(registre);
        Map<String, Boolean> facultatifs = new HashMap<>();
        for (int i = 0; description.containsProperty(champ(i, "nom")); i++) {
            facultatifs.put(
                    (String) description.getProperty(champ(i, "nom")),
                    Boolean.TRUE.equals(description.getProperty(champ(i, "facultatif"))));
        }

        List<JsonNode> personnes = personnes(registre);

        assertThat(personnes).hasSizeGreaterThanOrEqualTo(20);
        for (JsonNode personne : personnes) {
            assertThat(personne.propertyNames()).isSubsetOf(facultatifs.keySet());
            facultatifs.forEach((nom, facultatif) -> {
                if (!facultatif) {
                    assertThat(personne.hasNonNull(nom)).as(nom).isTrue();
                }
            });
        }
    }

    @Test
    void unePersonneALaMemeIdentiteDansTousLesRegistres() {
        Map<String, String> identites = new HashMap<>();
        for (String registre : REGISTRES) {
            List<String> identifiants = new ArrayList<>();
            for (JsonNode personne : personnes(registre)) {
                String identifiant = personne.get("identifiant_unique").asString();
                String identite =
                        personne.get("nom").asString()
                                + "|"
                                + personne.get("prenoms").asString()
                                + "|"
                                + personne.get("date_naissance").asString();
                identifiants.add(identifiant);
                assertThat(identites.merge(identifiant, identite, (a, b) -> a)).isEqualTo(identite);
            }
            assertThat(identifiants)
                    .doesNotHaveDuplicates()
                    .allMatch(i -> i.matches("990[0-9]{9}"));
        }
    }

    @Test
    void chaqueCompteCitoyenDeLaDemonstrationExisteDansLesQuatreRegistres() {
        List<String> citoyens =
                JSON.readTree(CITOYENS.toFile())
                        .get("users")
                        .valueStream()
                        .map(compte -> compte.get("attributes").get("iue").get(0).asString())
                        .toList();

        assertThat(citoyens).isNotEmpty();
        for (String registre : REGISTRES) {
            assertThat(personnes(registre))
                    .extracting(personne -> personne.get("identifiant_unique").asString())
                    .containsAll(citoyens);
        }
    }

    private static List<JsonNode> personnes(String registre) {
        return JSON.readTree(DONNEES.resolve(registre + "/personnes.json").toFile())
                .get("personnes")
                .valueStream()
                .toList();
    }

    private static String champ(int rang, String cle) {
        return "sira.registre.champs[" + rang + "]." + cle;
    }
}
