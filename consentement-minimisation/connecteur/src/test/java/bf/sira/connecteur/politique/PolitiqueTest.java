package bf.sira.connecteur.politique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import bf.sira.connecteur.regles.Chemin;
import bf.sira.connecteur.regles.Operateur;
import bf.sira.connecteur.regles.Regle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class PolitiqueTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir Path repertoire;

    private static Politique exemple(String registre) {
        return Politique.charger(Path.of("src/test/resources/politiques", registre + ".yaml"));
    }

    private static Regle regle(String champ, Operateur operateur, String valeur) {
        return Regle.de(champ, operateur, valeur == null ? null : JSON.readTree(valeur));
    }

    private static void assertRefus(Motif motif, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(Refus.class, r -> assertThat(r.motif()).isEqualTo(motif));
    }

    private Politique charger(String yaml) throws IOException {
        return Politique.charger(Files.writeString(repertoire.resolve("politique.yaml"), yaml));
    }

    @ParameterizedTest
    @ValueSource(strings = {"etat-civil", "nationalite", "casier", "diplomes"})
    void lesExemplesFournisSontValides(String registre) {
        Politique politique = exemple(registre);

        assertThat(politique.champs()).isNotEmpty();
        assertThat(politique.documents()).isNotEmpty();
    }

    // --- Niveau STATUT ----------------------------------------------------------------------

    @Test
    void uneQuestionAdmiseSurUnChampExposeEstAutorisee() {
        Politique etatCivil = exemple("etat-civil");

        assertThatCode(
                        () -> {
                            etatCivil.autoriser(
                                    regle("date_naissance", Operateur.AGE_AU_MOINS, "18"));
                            etatCivil.autoriser(regle("date_deces", Operateur.AUCUN, null));
                            // Un champ exposé en CHAMPS l'est à plus forte raison en STATUT.
                            etatCivil.autoriser(regle("sexe", Operateur.EGAL, "\"F\""));
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void unChampAbsentDeLaPolitiqueEstRefuse() {
        assertRefus(
                Motif.CHAMP_NON_EXPOSE,
                () -> exemple("etat-civil").autoriser(regle("religion", Operateur.EXISTE, null)));
        assertRefus(
                Motif.CHAMP_NON_EXPOSE,
                () ->
                        exemple("casier")
                                .autoriser(regle("condamnations.peine", Operateur.EXISTE, null)));
    }

    @Test
    void unOperateurNonAdmisEstRefuse() {
        // Demander la date exacte par égalité reviendrait à la deviner.
        assertRefus(
                Motif.QUESTION_NON_AUTORISEE,
                () ->
                        exemple("etat-civil")
                                .autoriser(
                                        regle("date_naissance", Operateur.EGAL, "\"2008-11-16\"")));
        assertRefus(
                Motif.QUESTION_NON_AUTORISEE,
                () ->
                        exemple("etat-civil")
                                .autoriser(
                                        regle(
                                                "date_naissance",
                                                Operateur.AU_MOINS,
                                                "\"2008-01-01\"")));
    }

    @Test
    void unSeuilNonAdmisEstRefuse() {
        // Sans cette borne, une suite de seuils (17, 18, 19…) révélerait l'âge exact.
        assertRefus(
                Motif.QUESTION_NON_AUTORISEE,
                () ->
                        exemple("etat-civil")
                                .autoriser(regle("date_naissance", Operateur.AGE_AU_MOINS, "19")));
        assertRefus(
                Motif.QUESTION_NON_AUTORISEE,
                () ->
                        exemple("nationalite")
                                .autoriser(regle("nationalite", Operateur.EGAL, "\"FR\"")));
        assertThatCode(() ->
                        exemple("nationalite")
                                .autoriser(regle("nationalite", Operateur.EGAL, "\"BF\"")))
                .doesNotThrowAnyException();
    }

    @Test
    void unSeuilEcritAutrementResteLeMemeSeuil() {
        assertThatCode(() ->
                        exemple("etat-civil")
                                .autoriser(regle("date_naissance", Operateur.AGE_AU_MOINS, "18")))
                .doesNotThrowAnyException();
    }

    @Test
    void unFiltreExigeQueLeChampFiltreSoitExpose() throws IOException {
        Politique diplomes = exemple("diplomes");
        assertThatCode(
                        () -> {
                            diplomes.autoriser(regle("diplomes[type=BAC]", Operateur.EXISTE, null));
                            diplomes.autoriser(
                                    regle(
                                            "diplomes[type=BAC].mention",
                                            Operateur.EGAL,
                                            "\"BIEN\""));
                        })
                .doesNotThrowAnyException();

        Politique sansType =
                charger(
                        """
                        validite: {par_defaut: P1D, maximale: P1D}
                        champs:
                          - {chemin: diplomes, niveau: STATUT}
                        documents: []
                        """);
        assertRefus(
                Motif.CHAMP_NON_EXPOSE,
                () -> sansType.autoriser(regle("diplomes[type=BAC]", Operateur.EXISTE, null)));
    }

    // --- Niveau CHAMPS ----------------------------------------------------------------------

    @Test
    void unChampExposeEnStatutNeSortPasEnChamps() {
        Politique etatCivil = exemple("etat-civil");

        assertThatCode(() ->
                        etatCivil.autoriser(List.of(Chemin.lire("nom"), Chemin.lire("prenoms"))))
                .doesNotThrowAnyException();
        assertRefus(
                Motif.NIVEAU_DEPASSE,
                () ->
                        etatCivil.autoriser(
                                List.of(Chemin.lire("nom"), Chemin.lire("date_naissance"))));
        assertRefus(
                Motif.CHAMP_NON_EXPOSE,
                () -> etatCivil.autoriser(List.of(Chemin.lire("religion"))));
        assertRefus(
                Motif.NIVEAU_DEPASSE,
                () ->
                        exemple("diplomes")
                                .autoriser(List.of(Chemin.lire("diplomes[type=BAC].mention"))));
    }

    // --- Niveau DOCUMENT --------------------------------------------------------------------

    @Test
    void seulsLesDocumentsListesSontDelivres() {
        Politique casier = exemple("casier");

        assertThatCode(() -> casier.autoriserDocument("bulletin-n3")).doesNotThrowAnyException();
        assertRefus(Motif.DOCUMENT_NON_AUTORISE, () -> casier.autoriserDocument("bulletin-n1"));
        assertRefus(Motif.DOCUMENT_NON_AUTORISE, () -> casier.autoriserDocument("../bulletin-n3"));
    }

    // --- Validité ---------------------------------------------------------------------------

    @Test
    void laValiditeDemandeeEstPlafonneeParLeRegistre() {
        Politique casier = exemple("casier");

        assertThat(casier.validite(null)).isEqualTo(Duration.ofDays(30));
        assertThat(casier.validite(Duration.ofDays(7))).isEqualTo(Duration.ofDays(7));
        assertThat(casier.validite(Duration.ofDays(3650))).isEqualTo(Duration.ofDays(90));
        assertRefus(Motif.JEU_INVALIDE, () -> casier.validite(Duration.ZERO));
        assertRefus(Motif.JEU_INVALIDE, () -> casier.validite(Duration.ofDays(-1)));
    }

    // --- Chargement -------------------------------------------------------------------------

    @Test
    void sansFichierLeConnecteurNeDemarrePas() {
        assertThatThrownBy(() -> Politique.charger(repertoire.resolve("absente.yaml")))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Fichier vide.
                "",
                // Section manquante.
                "validite: {par_defaut: P1D, maximale: P1D}\nchamps: []\n",
                // Clé inconnue : une restriction mal orthographiée ne doit pas disparaître.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom, niveau: STATUT, operateur: [EGAL]}\n",
                // Niveau inconnu, ou DOCUMENT sur un champ.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom, niveau: TOUT}\n",
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom, niveau: DOCUMENT}\n",
                // Niveau absent.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom}\n",
                // Chemin avec filtre, ou mal formé.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: 'diplomes[type=BAC]', niveau: STATUT}\n",
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: 'nom prenoms', niveau: STATUT}\n",
                // Champ déclaré deux fois.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom, niveau: STATUT}\n  - {chemin: nom, niveau: CHAMPS}\n",
                // Opérateur inconnu.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: []\nchamps:\n"
                        + "  - {chemin: nom, niveau: STATUT, operateurs: [CONTIENT]}\n",
                // Durées incohérentes.
                "validite: {par_defaut: P2D, maximale: P1D}\ndocuments: []\nchamps: []\n",
                "validite: {par_defaut: PT0S, maximale: P1D}\ndocuments: []\nchamps: []\n",
                "validite: {par_defaut: demain, maximale: P1D}\ndocuments: []\nchamps: []\n",
                // Type de document mal formé.
                "validite: {par_defaut: P1D, maximale: P1D}\ndocuments: ['../secret']\nchamps: []\n",
                // Objet arbitraire : le lecteur YAML ne construit aucune classe.
                "!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[]]]\n"
            })
    void unePolitiqueInvalideEmpecheLeDemarrage(String yaml) {
        assertThatThrownBy(() -> charger(yaml)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unePolitiqueQuiNExposeRienEstValide() throws IOException {
        Politique fermee =
                charger("validite: {par_defaut: P1D, maximale: P1D}\nchamps: []\ndocuments: []\n");

        assertRefus(
                Motif.CHAMP_NON_EXPOSE,
                () -> fermee.autoriser(regle("nom", Operateur.EXISTE, null)));
        assertThat(fermee.champs()).isEmpty();
    }
}
