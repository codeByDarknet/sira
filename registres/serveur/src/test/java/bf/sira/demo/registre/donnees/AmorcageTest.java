package bf.sira.demo.registre.donnees;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Ce que la passerelle demandera (amorçage), ce que chaque connecteur laisse sortir (politique) et
 * ce que chaque registre contient (description) doivent s'accorder : sinon la démonstration échoue
 * au premier appel.
 */
class AmorcageTest {

    /** Le dossier {@code registres/}, vu depuis ce module. */
    private static final Path DONNEES = Path.of("..");

    /** L'amorçage du référentiel de démonstration, rangé avec la passerelle. */
    private static final String AMORCAGE = "../passerelle/amorcage-demo/";

    @ParameterizedTest
    @ValueSource(strings = {"complet", "sans-diplomes"})
    void chaqueJeuEstAdmisParLaPolitiqueEtConnuDuRegistre(String variante) {
        Map<String, Object> amorcage = lire(AMORCAGE + variante + ".yaml");
        List<String> registres = codes(amorcage, "registres");

        for (Map<String, Object> jeu : liste(amorcage, "jeux")) {
            String registre = (String) jeu.get("registre");
            assertThat(registres).contains(registre);
            Map<String, Object> politique = lire("connecteurs/" + registre + ".yaml");
            Map<String, Object> description = description(registre);
            String code = (String) jeu.get("code");
            switch ((String) jeu.get("niveau")) {
                case "STATUT" -> {
                    Map<String, Object> regle = objet(jeu.get("regle"));
                    Map<String, Object> expose = expose(politique, (String) regle.get("champ"));
                    assertThat(noms(registre)).as(code).contains((String) regle.get("champ"));
                    assertThat(listeSimple(expose.get("operateurs")))
                            .as(code)
                            .contains(regle.get("operateur"));
                    // EXISTE et AUCUN ne comparent à aucune valeur.
                    if (regle.containsKey("valeur")) {
                        assertThat(listeSimple(expose.get("valeurs")))
                                .as(code)
                                .contains(regle.get("valeur"));
                    }
                }
                case "CHAMPS" -> {
                    for (Object champ : listeSimple(jeu.get("champs"))) {
                        assertThat(noms(registre)).as(code).contains((String) champ);
                        assertThat(expose(politique, (String) champ).get("niveau"))
                                .as(code)
                                .isEqualTo("CHAMPS");
                    }
                }
                default -> {
                    assertThat(listeSimple(politique.get("documents")))
                            .as(code)
                            .contains(jeu.get("document"));
                    assertThat(objet(description.get("documents")))
                            .as(code)
                            .containsKey((String) jeu.get("document"));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"complet", "sans-diplomes"})
    void chaqueHabilitationPorteSurDesJeuxEtUneDemarcheDeclares(String variante) {
        Map<String, Object> amorcage = lire(AMORCAGE + variante + ".yaml");
        List<String> membres = codes(amorcage, "membres");
        List<String> jeux = codes(amorcage, "jeux");

        assertThat(liste(amorcage, "registres"))
                .allSatisfy(
                        registre -> assertThat(membres).contains((String) registre.get("membre")));
        assertThat(liste(amorcage, "demarches"))
                .allSatisfy(
                        demarche -> assertThat(membres).contains((String) demarche.get("service")));
        assertThat(liste(amorcage, "habilitations"))
                .hasSize(3)
                .allSatisfy(habilitation -> {
                    assertThat(codes(amorcage, "demarches"))
                            .contains((String) habilitation.get("demarche"));
                    assertThat(jeux)
                            .containsAll(
                                    listeSimple(habilitation.get("jeux")).stream()
                                            .map(String.class::cast)
                                            .toList());
                    assertThat(habilitation.get("etat")).isEqualTo("APPROUVEE");
                });
    }

    @Test
    void laVarianteSansDiplomesNeDitRienDuRegistreDesDiplomes() {
        Map<String, Object> complet = lire(AMORCAGE + "complet.yaml");
        Map<String, Object> sans = lire(AMORCAGE + "sans-diplomes.yaml");

        assertThat(codes(complet, "registres")).contains("diplomes");
        assertThat(codes(sans, "registres")).containsExactly("etat-civil", "nationalite", "casier");
        assertThat(codes(sans, "jeux")).noneMatch(code -> code.startsWith("diplomes"));
        assertThat(liste(complet, "jeux")).containsAll(liste(sans, "jeux"));
        assertThat(liste(complet, "membres")).containsAll(liste(sans, "membres"));
        assertThat(sans.get("demarches")).isEqualTo(complet.get("demarches"));
        assertThat(sans.get("habilitations").toString()).doesNotContain("diplomes.");
    }

    private static Map<String, Object> description(String registre) {
        Map<String, Object> racine = lire("donnees/" + registre + "/registre.yaml");
        return objet(objet(racine.get("sira")).get("registre"));
    }

    /**
     * Les noms de champs que le connecteur de ce registre connaît : ceux de la correspondance de
     * son fichier de raccordement s'il en a un (chacun pointant vers un champ du contrat commun),
     * ceux de la description du registre sinon (API interne, fiche entière).
     */
    private static List<String> noms(String registre) {
        Path raccordement = DONNEES.resolve("raccordements/" + registre + ".yaml");
        if (Files.exists(raccordement)) {
            return List.copyOf(
                    objet(lire("raccordements/" + registre + ".yaml").get("correspondance"))
                            .keySet());
        }
        return codes(description(registre), "champs", "nom");
    }

    private static Map<String, Object> expose(Map<String, Object> politique, String chemin) {
        return liste(politique, "champs").stream()
                .filter(champ -> chemin.equals(champ.get("chemin")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("champ non exposé : " + chemin));
    }

    private static List<String> codes(Map<String, Object> source, String cle) {
        return codes(source, cle, "code");
    }

    private static List<String> codes(Map<String, Object> source, String cle, String propriete) {
        return liste(source, cle).stream().map(e -> (String) e.get(propriete)).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Map<String, Object> source, String cle) {
        return (List<Map<String, Object>>) source.get(cle);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listeSimple(Object valeur) {
        return (List<Object>) valeur;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objet(Object valeur) {
        return (Map<String, Object>) valeur;
    }

    private static Map<String, Object> lire(String fichier) {
        try (Reader lecteur = Files.newBufferedReader(DONNEES.resolve(fichier))) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(lecteur);
        } catch (IOException e) {
            throw new IllegalStateException(fichier, e);
        }
    }
}
