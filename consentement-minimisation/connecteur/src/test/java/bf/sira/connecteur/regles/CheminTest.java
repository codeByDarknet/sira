package bf.sira.connecteur.regles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.connecteur.Refus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CheminTest {

    private static final JsonNode FICHE =
            JsonMapper.builder()
                    .build()
                    .readTree(
                            """
                            {
                              "nom": "SAWADOGO",
                              "surnom": null,
                              "adresse": {"commune": "Koudougou"},
                              "condamnations": [],
                              "diplomes": [
                                {"type": "BAC", "annee": 2015, "mention": "BIEN"},
                                {"type": "LICENCE", "annee": 2018, "mention": null},
                                null
                              ]
                            }
                            """);

    private static java.util.List<String> valeurs(String chemin) {
        return Chemin.lire(chemin).resoudre(FICHE).valeurs().stream()
                .map(JsonNode::toString)
                .toList();
    }

    @Test
    void unNomDesigneUnChampSimple() {
        assertThat(valeurs("nom")).containsExactly("\"SAWADOGO\"");
        assertThat(valeurs("adresse.commune")).containsExactly("\"Koudougou\"");
        assertThat(Chemin.lire("nom").resoudre(FICHE).liste()).isFalse();
    }

    @Test
    void unChampAbsentOuNulNeDesigneRien() {
        assertThat(valeurs("prenoms")).isEmpty();
        assertThat(valeurs("surnom")).isEmpty();
        assertThat(valeurs("nom.premier")).isEmpty();
        assertThat(valeurs("adresse.commune.quartier")).isEmpty();
    }

    @Test
    void uneListeEstParcourueElementParElement() {
        assertThat(valeurs("diplomes.type")).containsExactly("\"BAC\"", "\"LICENCE\"");
        assertThat(valeurs("diplomes.mention")).containsExactly("\"BIEN\"");
        assertThat(valeurs("condamnations")).isEmpty();
        assertThat(Chemin.lire("condamnations").resoudre(FICHE).liste()).isTrue();
    }

    @Test
    void unFiltreNeRetientQueLesElementsEgaux() {
        assertThat(valeurs("diplomes[type=BAC].mention")).containsExactly("\"BIEN\"");
        assertThat(valeurs("diplomes[annee=2018].type")).containsExactly("\"LICENCE\"");
        assertThat(valeurs("diplomes[type=MASTER]")).isEmpty();
        assertThat(valeurs("adresse[commune=Koudougou].commune")).containsExactly("\"Koudougou\"");
        assertThat(valeurs("nom[type=BAC]")).isEmpty();
    }

    @Test
    void laPolitiqueVoitLeChampSansFiltreEtLesChampsFiltres() {
        Chemin chemin = Chemin.lire("diplomes[type=BAC].mention");

        assertThat(chemin.sansFiltres()).isEqualTo("diplomes.mention");
        assertThat(chemin.champsDesFiltres()).containsExactly("diplomes.type");
        assertThat(Chemin.lire("nom").champsDesFiltres()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                ".",
                "nom.",
                ".nom",
                "nom..prenoms",
                "1nom",
                "nom prenoms",
                "diplomes[0]",
                "diplomes[*]",
                "diplomes[type=]",
                "diplomes[type=BAC",
                "diplomes[type=BAC][annee=2015]",
                "diplomes[type==BAC]",
                "diplomes[type='BAC']",
                "diplomes[?(@.annee>2000)]",
                "$.nom",
                "nom/../secret",
                "T(java.lang.Runtime).getRuntime()",
                "#{nom}",
                "${nom}",
                "a.b.c.d.e.f"
            })
    void toutCeQuiNEstPasUnCheminEstRefuse(String texte) {
        assertThatThrownBy(() -> Chemin.lire(texte)).isInstanceOf(Refus.class);
    }

    @Test
    void unCheminTropLongOuAbsentEstRefuse() {
        assertThatThrownBy(() -> Chemin.lire(null)).isInstanceOf(Refus.class);
        assertThatThrownBy(() -> Chemin.lire("a".repeat(65))).isInstanceOf(Refus.class);
        assertThatThrownBy(() -> Chemin.lire(("a".repeat(60) + ".").repeat(3) + "b"))
                .isInstanceOf(Refus.class);
    }
}
