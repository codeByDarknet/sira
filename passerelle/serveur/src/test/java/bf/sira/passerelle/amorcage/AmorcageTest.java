package bf.sira.passerelle.amorcage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.passerelle.PasserelleDeTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/** L'amorçage par fichiers : l'exemple livré s'importe, un fichier fautif arrête le démarrage. */
class AmorcageTest extends PasserelleDeTest {

    @Autowired Amorcage amorcage;
    @Autowired JdbcClient base;
    @TempDir Path repertoire;

    private Path fichier(String nom, String contenu) throws IOException {
        return Files.writeString(repertoire.resolve(nom), contenu);
    }

    private static Path exemple() throws Exception {
        return Path.of(AmorcageTest.class.getResource("/amorcage/exemple.yaml").toURI());
    }

    private long compter(String table) {
        return base.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void lExempleLivreEstCompletEtAEteImporteAuDemarrage() throws Exception {
        Amorcage.Fichier exemple = amorcage.lire(exemple());

        assertThat(exemple.membres())
                .extracting(m -> m.code())
                .containsExactlyInAnyOrder(
                        "concours", "bourses", "etat-civil", "nationalite", "casier", "diplomes");
        assertThat(exemple.registres()).hasSize(4);
        assertThat(exemple.jeux()).hasSize(6);
        assertThat(exemple.demarches())
                .extracting(d -> d.code())
                .containsExactlyInAnyOrder("concours-direct", "bourse-etudes");
        assertThat(exemple.habilitations()).hasSize(2);
        assertThat(exemple.habilitations().getFirst().saisie().jeux()).isNotEmpty();

        assertThat(compter("registre")).isEqualTo(4);
        assertThat(
                        compter(
                                "habilitation WHERE etat = 'APPROUVEE' AND instruite_par = 'Installation de Sira'"))
                .isEqualTo(2);
    }

    @Test
    void unReferentielDejaRempliNEstPasReimporte() {
        long membres = compter("membre");
        long lignes = compter("journal");

        amorcage.run(new DefaultApplicationArguments());

        assertThat(compter("membre")).isEqualTo(membres);
        assertThat(compter("journal")).isEqualTo(lignes);
    }

    @Test
    void leMemeContenuSEcritEnJson() throws Exception {
        Amorcage.Fichier lu =
                amorcage.lire(
                        fichier(
                                "membres.json",
                                """
                                {"membres": [{"code": "impots", "nom": "Quitus", "organisme": "DGI",
                                  "roles": ["SERVICE"], "identite": "urn:sira:service:impots"}]}
                                """));

        assertThat(lu.membres().getFirst().identite()).isEqualTo("urn:sira:service:impots");
        assertThat(lu.jeux()).isEmpty();
    }

    @Test
    void lesAdressesPubliquesSuiventLeDeploiement() throws Exception {
        String membre =
                "membres:\n  - code: impots\n    nom: Quitus\n    organisme: DGI\n"
                        + "    roles: [SERVICE]\n    identite: urn:sira:service:impots\n"
                        + "    retours_autorises:\n";

        Amorcage.Fichier lu =
                amorcage.lire(
                        fichier(
                                "port.yaml",
                                membre
                                        + "      - http://impots.sira.localhost:"
                                        + "${SIRA_PORT_INCONNU_DES_TESTS:8600}/retour\n"));

        assertThat(lu.membres().getFirst().retoursAutorises())
                .containsExactly("http://impots.sira.localhost:8600/retour");
        assertThatThrownBy(() ->
                        amorcage.lire(
                                fichier(
                                        "sans-defaut.yaml",
                                        membre + "      - http://h:${SIRA_VARIABLE_ABSENTE}/r\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sans-defaut.yaml");
    }

    @Test
    void uneCleInconnueUneValeurInvalideOuUnTypeJavaSontRefuses() throws Exception {
        String membre = "membres:\n  - code: impots\n    nom: Quitus\n    organisme: DGI\n";

        assertThatThrownBy(() ->
                        amorcage.lire(
                                fichier(
                                        "a.yaml",
                                        membre + "    roles: [SERVICE]\n    actiff: true\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a.yaml");
        assertThatThrownBy(() -> amorcage.lire(fichier("b.yaml", membre + "    roles: []\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles");
        assertThatThrownBy(() ->
                        amorcage.lire(
                                fichier(
                                        "c.yaml",
                                        "habilitations:\n  - service: impots\n    demarche: quitus\n"
                                                + "    jeux: [casier.neant]\n    justification: Loi\n"
                                                + "    etat: BROUILLON\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("etat");
        assertThatThrownBy(() -> amorcage.lire(fichier("d.yaml", "membres: !!java.util.Date 0\n")))
                .isInstanceOf(IllegalStateException.class);
    }
}
