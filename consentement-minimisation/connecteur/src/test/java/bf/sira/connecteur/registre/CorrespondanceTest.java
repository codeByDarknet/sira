package bf.sira.connecteur.registre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CorrespondanceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode PERSONNE =
            JSON.readTree(
                    """
                    {"birth": {"date": "2001-02-03", "place": "Gaoua"},
                     "citizenships": [{"country": "BF"}, {"country": "CI"}, {"other": 1}],
                     "a/b": {"c~d": "échappé"},
                     "secret": "NE-DOIT-PAS-SORTIR",
                     "rien": null}
                    """);

    @Test
    void pointeurEtCheminSimpleDesignentLaMemeValeur() {
        assertThat(Correspondance.lire(PERSONNE, "/birth/date").asString()).isEqualTo("2001-02-03");
        assertThat(Correspondance.lire(PERSONNE, "birth.date").asString()).isEqualTo("2001-02-03");
        assertThat(Correspondance.lire(PERSONNE, "/citizenships/1/country").asString())
                .isEqualTo("CI");
        assertThat(Correspondance.lire(PERSONNE, "citizenships[1].country").asString())
                .isEqualTo("CI");
        assertThat(Correspondance.lire(PERSONNE, "/a~1b/c~0d").asString()).isEqualTo("échappé");
    }

    @Test
    void lEtoileParcourtUneListeEtRendToujoursUneListe() {
        assertThat(Correspondance.lire(PERSONNE, "/citizenships/*/country").toString())
                .isEqualTo("[\"BF\",\"CI\"]");
        assertThat(Correspondance.lire(PERSONNE, "citizenships[*].country").toString())
                .isEqualTo("[\"BF\",\"CI\"]");
        assertThat(Correspondance.lire(PERSONNE, "/absente/*/x").toString()).isEqualTo("[]");
    }

    @Test
    void unEmplacementAbsentOuNulLaisseLeChampAbsent() {
        assertThat(Correspondance.lire(PERSONNE, "/birth/heure")).isNull();
        assertThat(Correspondance.lire(PERSONNE, "/rien")).isNull();
        assertThat(Correspondance.lire(PERSONNE, "/citizenships/9/country")).isNull();
        assertThat(Correspondance.lire(PERSONNE, "/birth/date/plus")).isNull();
    }

    @Test
    void seulsLesChampsNommesSontRecopies() {
        JsonNode fiche =
                Correspondance.traduire(
                        PERSONNE,
                        Map.of(
                                "date_naissance", "/birth/date",
                                "nationalite", "/citizenships/*/country",
                                "absent", "/nulle/part"));

        assertThat(fiche.propertyNames())
                .containsExactlyInAnyOrder("date_naissance", "nationalite");
        assertThat(fiche.toString()).doesNotContain("NE-DOIT-PAS-SORTIR", "Gaoua");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a..b", "a[", "a[x]", "//b", "a.", "[*]"})
    void uneExpressionIllisibleEstRefuseeAuChargement(String expression) {
        assertThatThrownBy(() -> Correspondance.verifier(expression))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
