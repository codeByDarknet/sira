package bf.sira.demo.service.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Le même serveur sert les trois démarches de la démonstration, une par profil Spring. Chaque
 * profil doit être valide et s'accorder avec l'amorçage de la passerelle : même code de service,
 * même nom, même organisme, et une démarche habilitée pour ce service.
 */
class ProfilsTest {

    /** L'amorçage de démonstration, rangé avec la passerelle. */
    private static final Path AMORCAGE = Path.of("../../passerelle/amorcage-demo/complet.yaml");

    @ParameterizedTest
    @ValueSource(strings = {"concours", "bourses", "entreprises"})
    void chaqueProfilEstValideEtDeclareALaPasserelle(String profil) throws IOException {
        ProprietesService service = lire(profil);
        try (ValidatorFactory fabrique = Validation.buildDefaultValidatorFactory()) {
            Validator validateur = fabrique.getValidator();
            assertThat(validateur.validate(service)).as(profil).isEmpty();
        }
        assertThat(service.code()).isEqualTo(profil);

        Map<String, Object> amorcage = amorcage();
        Map<String, Object> membre = element(amorcage, "membres", "code", service.code());
        assertThat(membre.get("nom")).isEqualTo(service.nom());
        assertThat(membre.get("organisme")).isEqualTo(service.organisme());
        assertThat(membre.get("roles")).isEqualTo(List.of("SERVICE"));

        Map<String, Object> demarche = element(amorcage, "demarches", "code", service.demarche());
        assertThat(demarche.get("service")).isEqualTo(service.code());
        Map<String, Object> habilitation =
                element(amorcage, "habilitations", "demarche", service.demarche());
        assertThat(habilitation.get("service")).isEqualTo(service.code());
        assertThat(habilitation.get("etat")).isEqualTo("APPROUVEE");
    }

    /**
     * Le formulaire de chaque profil est valide, et les champs annoncés pour chaque jeu de niveau
     * {@code CHAMPS} sont ceux que l'amorçage déclare, pour tous les jeux habilités du service.
     */
    @ParameterizedTest
    @ValueSource(strings = {"concours", "bourses", "entreprises"})
    void leFormulaireDeChaqueProfilSAccordeAvecLesJeux(String profil) throws IOException {
        ProprietesFormulaire formulaire =
                lier(profil, "sira.formulaire", ProprietesFormulaire.class);
        try (ValidatorFactory fabrique = Validation.buildDefaultValidatorFactory()) {
            assertThat(fabrique.getValidator().validate(formulaire)).as(profil).isEmpty();
        }
        assertThat(formulaire.declarations()).as(profil).isNotEmpty();
        assertThat(formulaire.declarations())
                .extracting(ProprietesFormulaire.Declaration::code)
                .doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).doesNotContain("."));

        Map<String, Object> amorcage = amorcage();
        formulaire
                .champs()
                .forEach((jeu, champs) ->
                        assertThat(element(amorcage, "jeux", "code", jeu).get("champs"))
                                .as(jeu)
                                .isEqualTo(champs));
        ProprietesService service = lire(profil);
        @SuppressWarnings("unchecked")
        List<String> habilites =
                (List<String>)
                        element(amorcage, "habilitations", "demarche", service.demarche())
                                .get("jeux");
        for (String jeu : habilites) {
            if ("CHAMPS".equals(element(amorcage, "jeux", "code", jeu).get("niveau"))) {
                assertThat(formulaire.champs()).as(jeu).containsKey(jeu);
            }
        }
    }

    /** Les propriétés {@code sira.service} du profil, telles que Spring les lierait. */
    private static ProprietesService lire(String profil) throws IOException {
        return lier(profil, "sira.service", ProprietesService.class);
    }

    private static <T> T lier(String profil, String prefixe, Class<T> type) throws IOException {
        StandardEnvironment environnement = new StandardEnvironment();
        YamlPropertySourceLoader chargeur = new YamlPropertySourceLoader();
        for (String fichier : List.of("application-" + profil + ".yaml", "application.yaml")) {
            chargeur.load(fichier, new ClassPathResource(fichier))
                    .forEach(source -> environnement.getPropertySources().addLast(source));
        }
        return Binder.get(environnement).bindOrCreate(prefixe, type);
    }

    private static Map<String, Object> element(
            Map<String, Object> amorcage, String liste, String cle, String valeur) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) amorcage.get(liste);
        return elements.stream()
                .filter(e -> valeur.equals(e.get(cle)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(liste + " : " + valeur + " absent"));
    }

    private static Map<String, Object> amorcage() throws IOException {
        try (Reader lecteur = Files.newBufferedReader(AMORCAGE)) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(lecteur);
        }
    }
}
