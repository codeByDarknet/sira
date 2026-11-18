package bf.sira.connecteur.regles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MoteurReglesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId OUAGADOUGOU = ZoneId.of("Africa/Ouagadougou");

    /** Le 16 novembre 2026 à midi, à Ouagadougou. */
    private static final MoteurRegles MOTEUR = moteur("2026-11-16T12:00:00Z");

    private static final JsonNode FICHE =
            JSON.readTree(
                    """
                    {
                      "nom": "SAWADOGO",
                      "sexe": "F",
                      "date_naissance": "2008-11-16",
                      "date_deces": null,
                      "enfants": 2,
                      "revenu": 150000.50,
                      "boursier": true,
                      "adresse": {"commune": "Koudougou"},
                      "condamnations": [],
                      "certificat": {"valide_jusqu_au": "2026-11-16"},
                      "diplomes": [
                        {"type": "BAC", "annee": 2015, "mention": "BIEN", "obtenu_le": "2015-07-10"},
                        {"type": "LICENCE", "annee": 2018, "mention": "PASSABLE"}
                      ]
                    }
                    """);

    private static MoteurRegles moteur(String instant) {
        return new MoteurRegles(Clock.fixed(Instant.parse(instant), OUAGADOUGOU), OUAGADOUGOU);
    }

    private static boolean evaluer(MoteurRegles moteur, String fiche, String regle) {
        JsonNode r = JSON.readTree(regle);
        return moteur.evaluer(
                Regle.de(
                        r.get("champ").stringValue(),
                        Operateur.valueOf(r.get("operateur").stringValue()),
                        r.get("valeur")),
                fiche == null ? FICHE : JSON.readTree(fiche));
    }

    private static boolean evaluer(String champ, Operateur operateur, String valeur) {
        return MOTEUR.evaluer(
                Regle.de(champ, operateur, valeur == null ? null : JSON.readTree(valeur)), FICHE);
    }

    private static void assertRefus(Motif motif, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(Refus.class, r -> assertThat(r.motif()).isEqualTo(motif));
    }

    // --- Âges -------------------------------------------------------------------------------

    @ParameterizedTest(name = "né le {0}, le {1} : au moins {2} ans = {3}")
    @CsvSource({
        // Le jour même des 18 ans, on est majeur ; la veille, non.
        "2008-11-16, 2026-11-16T00:00:00Z, 18, true",
        "2008-11-17, 2026-11-16T23:59:59Z, 18, false",
        "2008-11-15, 2026-11-16T12:00:00Z, 18, true",
        // Né un 29 février : l'anniversaire d'une année ordinaire tombe le 1er mars.
        "2008-02-29, 2026-02-28T12:00:00Z, 18, false",
        "2008-02-29, 2026-03-01T00:00:00Z, 18, true",
        "2008-02-29, 2028-02-29T00:00:00Z, 20, true",
        "2008-02-29, 2028-02-28T23:59:59Z, 20, false",
        // Fin et début d'année.
        "2008-12-31, 2026-12-30T12:00:00Z, 18, false",
        "2008-12-31, 2026-12-31T00:00:00Z, 18, true",
        "2009-01-01, 2026-12-31T23:59:59Z, 18, false",
        // Né aujourd'hui : zéro an.
        "2026-11-16, 2026-11-16T12:00:00Z, 0, true",
        "2026-11-16, 2026-11-16T12:00:00Z, 1, false",
    })
    void ageAuMoins(String naissance, String instant, int seuil, boolean attendu) {
        String fiche = "{\"date_naissance\": \"%s\"}".formatted(naissance);
        String regle =
                "{\"champ\":\"date_naissance\",\"operateur\":\"AGE_AU_MOINS\",\"valeur\":%d}"
                        .formatted(seuil);

        assertThat(evaluer(moteur(instant), fiche, regle)).isEqualTo(attendu);
    }

    @ParameterizedTest(name = "né le {0}, le {1} : au plus {2} ans = {3}")
    @CsvSource({
        // « Au plus 35 ans » reste vrai jusqu'à la veille des 36 ans.
        "1990-11-17, 2026-11-16T12:00:00Z, 35, true",
        "1990-11-16, 2026-11-16T00:00:00Z, 35, false",
        "1991-11-16, 2026-11-16T12:00:00Z, 35, true",
    })
    void ageAuPlus(String naissance, String instant, int seuil, boolean attendu) {
        String fiche = "{\"date_naissance\": \"%s\"}".formatted(naissance);
        String regle =
                "{\"champ\":\"date_naissance\",\"operateur\":\"AGE_AU_PLUS\",\"valeur\":%d}"
                        .formatted(seuil);

        assertThat(evaluer(moteur(instant), fiche, regle)).isEqualTo(attendu);
    }

    @Test
    void leJourEstCeluiDuFuseauDuRegistre() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Instant instant = Instant.parse("2026-11-15T20:00:00Z"); // déjà le 16 à Tokyo
        Regle majeur = Regle.de("date_naissance", Operateur.AGE_AU_MOINS, JSON.readTree("18"));

        assertThat(new MoteurRegles(Clock.fixed(instant, tokyo), tokyo).evaluer(majeur, FICHE))
                .isTrue();
        assertThat(
                        new MoteurRegles(Clock.fixed(instant, OUAGADOUGOU), OUAGADOUGOU)
                                .evaluer(majeur, FICHE))
                .isFalse();
    }

    @Test
    void uneDateAvecHeureEstAcceptee() {
        String fiche = "{\"date_naissance\": \"2008-11-16T00:00:00Z\"}";
        String regle =
                "{\"champ\":\"date_naissance\",\"operateur\":\"AGE_AU_MOINS\",\"valeur\":18}";

        assertThat(evaluer(MOTEUR, fiche, regle)).isTrue();
    }

    @Test
    void uneNaissanceFutureOuIllisibleFaitRefuserLaRegle() {
        String regle =
                "{\"champ\":\"date_naissance\",\"operateur\":\"AGE_AU_MOINS\",\"valeur\":18}";

        for (String valeur :
                List.of("\"2026-11-17\"", "\"16/11/2008\"", "\"2008-02-30\"", "20081116", "{}")) {
            assertRefus(
                    Motif.REGLE_INAPPLICABLE,
                    () -> evaluer(MOTEUR, "{\"date_naissance\": " + valeur + "}", regle));
        }
    }

    @Test
    void unRefusNeCiteJamaisLaValeurLue() {
        String regle =
                "{\"champ\":\"date_naissance\",\"operateur\":\"AGE_AU_MOINS\",\"valeur\":18}";

        assertThatThrownBy(() -> evaluer(MOTEUR, "{\"date_naissance\": \"16/11/2008\"}", regle))
                .isInstanceOfSatisfying(
                        Refus.class,
                        refus -> {
                            assertThat(refus.getMessage()).doesNotContain("2008");
                            assertThat(refus.detail()).doesNotContain("2008");
                            assertThat(refus.getCause()).isNull();
                        });
    }

    // --- Champs absents et listes vides -----------------------------------------------------

    @Test
    void unChampAbsentNeSatisfaitAucuneComparaison() {
        assertThat(evaluer("date_mariage", Operateur.AGE_AU_MOINS, "18")).isFalse();
        assertThat(evaluer("date_mariage", Operateur.AGE_AU_PLUS, "18")).isFalse();
        assertThat(evaluer("date_mariage", Operateur.ENCORE_VALIDE, null)).isFalse();
        assertThat(evaluer("prenoms", Operateur.EGAL, "\"Awa\"")).isFalse();
        assertThat(evaluer("salaire", Operateur.AU_MOINS, "0")).isFalse();
        assertThat(evaluer("salaire", Operateur.AU_PLUS, "0")).isFalse();
        assertThat(evaluer("date_deces", Operateur.AGE_AU_MOINS, "0")).isFalse();
    }

    @Test
    void existeEtAucunSurChampAbsentNulOuListeVide() {
        assertThat(evaluer("nom", Operateur.EXISTE, null)).isTrue();
        assertThat(evaluer("nom", Operateur.AUCUN, null)).isFalse();
        // Champ nul et champ absent : rien n'existe.
        assertThat(evaluer("date_deces", Operateur.EXISTE, null)).isFalse();
        assertThat(evaluer("date_deces", Operateur.AUCUN, null)).isTrue();
        assertThat(evaluer("date_mariage", Operateur.AUCUN, null)).isTrue();
        // Liste vide : casier vierge.
        assertThat(evaluer("condamnations", Operateur.AUCUN, null)).isTrue();
        assertThat(evaluer("condamnations", Operateur.EXISTE, null)).isFalse();
        // Liste non vide, puis filtrée.
        assertThat(evaluer("diplomes", Operateur.EXISTE, null)).isTrue();
        assertThat(evaluer("diplomes[type=BAC]", Operateur.EXISTE, null)).isTrue();
        assertThat(evaluer("diplomes[type=MASTER]", Operateur.EXISTE, null)).isFalse();
        assertThat(evaluer("diplomes[type=MASTER]", Operateur.AUCUN, null)).isTrue();
    }

    @Test
    void uneListeDeCondamnationsNonVideNEstPasUnCasierVierge() {
        String fiche = "{\"condamnations\": [{\"date\": \"2020-01-01\", \"peine\": \"amende\"}]}";
        String regle = "{\"champ\":\"condamnations\",\"operateur\":\"AUCUN\"}";

        assertThat(evaluer(MOTEUR, fiche, regle)).isFalse();
    }

    // --- Comparaisons -----------------------------------------------------------------------

    @Test
    void egalCompareTextesNombresEtBooleens() {
        assertThat(evaluer("sexe", Operateur.EGAL, "\"F\"")).isTrue();
        assertThat(evaluer("sexe", Operateur.EGAL, "\"f\"")).isFalse();
        assertThat(evaluer("enfants", Operateur.EGAL, "2")).isTrue();
        assertThat(evaluer("enfants", Operateur.EGAL, "2.0")).isTrue();
        assertThat(evaluer("enfants", Operateur.EGAL, "3")).isFalse();
        assertThat(evaluer("boursier", Operateur.EGAL, "true")).isTrue();
        assertThat(evaluer("boursier", Operateur.EGAL, "false")).isFalse();
        assertThat(evaluer("adresse.commune", Operateur.EGAL, "\"Koudougou\"")).isTrue();
    }

    @Test
    void surUneListeUneSeuleValeurSuffit() {
        assertThat(evaluer("diplomes.type", Operateur.EGAL, "\"LICENCE\"")).isTrue();
        assertThat(evaluer("diplomes.type", Operateur.EGAL, "\"MASTER\"")).isFalse();
        assertThat(evaluer("diplomes[type=BAC].mention", Operateur.EGAL, "\"BIEN\"")).isTrue();
        assertThat(evaluer("diplomes[type=LICENCE].mention", Operateur.EGAL, "\"BIEN\"")).isFalse();
        assertThat(evaluer("diplomes.annee", Operateur.AU_MOINS, "2018")).isTrue();
        assertThat(evaluer("diplomes.annee", Operateur.AU_MOINS, "2019")).isFalse();
        assertThat(evaluer("diplomes.annee", Operateur.AU_PLUS, "2015")).isTrue();
        assertThat(evaluer("diplomes.annee", Operateur.AU_PLUS, "2014")).isFalse();
    }

    @Test
    void auMoinsEtAuPlusIncluentLaBorne() {
        assertThat(evaluer("enfants", Operateur.AU_MOINS, "2")).isTrue();
        assertThat(evaluer("enfants", Operateur.AU_MOINS, "3")).isFalse();
        assertThat(evaluer("enfants", Operateur.AU_PLUS, "2")).isTrue();
        assertThat(evaluer("enfants", Operateur.AU_PLUS, "1")).isFalse();
        assertThat(evaluer("revenu", Operateur.AU_MOINS, "150000.5")).isTrue();
        assertThat(evaluer("revenu", Operateur.AU_PLUS, "150000.49")).isFalse();
    }

    @Test
    void auMoinsEtAuPlusComparentAussiDesDates() {
        assertThat(evaluer("diplomes.obtenu_le", Operateur.AU_MOINS, "\"2015-07-10\"")).isTrue();
        assertThat(evaluer("diplomes.obtenu_le", Operateur.AU_MOINS, "\"2015-07-11\"")).isFalse();
        assertThat(evaluer("diplomes.obtenu_le", Operateur.AU_PLUS, "\"2015-07-09\"")).isFalse();
    }

    @Test
    void encoreValideInclutLeJourDEcheance() {
        assertThat(evaluer("certificat.valide_jusqu_au", Operateur.ENCORE_VALIDE, null)).isTrue();
        assertThat(
                        evaluer(
                                moteur("2026-11-17T00:00:00Z"),
                                null,
                                "{\"champ\":\"certificat.valide_jusqu_au\","
                                        + "\"operateur\":\"ENCORE_VALIDE\"}"))
                .isFalse();
    }

    @Test
    void unTypeInattenduFaitRefuserLaRegle() {
        assertRefus(Motif.REGLE_INAPPLICABLE, () -> evaluer("nom", Operateur.EGAL, "12"));
        assertRefus(Motif.REGLE_INAPPLICABLE, () -> evaluer("enfants", Operateur.EGAL, "\"2\""));
        assertRefus(Motif.REGLE_INAPPLICABLE, () -> evaluer("nom", Operateur.AU_MOINS, "3"));
        assertRefus(Motif.REGLE_INAPPLICABLE, () -> evaluer("adresse", Operateur.EGAL, "\"x\""));
        assertRefus(Motif.REGLE_INAPPLICABLE, () -> evaluer("nom", Operateur.ENCORE_VALIDE, null));
        // Une seule valeur illisible suffit, même si une autre satisfait la règle.
        assertRefus(
                Motif.REGLE_INAPPLICABLE,
                () ->
                        evaluer(
                                MOTEUR,
                                "{\"titres\": [{\"fin\": \"2030-01-01\"}, {\"fin\": \"bientôt\"}]}",
                                "{\"champ\":\"titres.fin\",\"operateur\":\"ENCORE_VALIDE\"}"));
    }

    @Test
    void uneValeurQuiNeConvientPasALOperateurEstRefusee() {
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.EXISTE, "\"x\""));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.EGAL, null));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.EGAL, "[1]"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.EGAL, "{\"a\":1}"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.AU_MOINS, "\"abc\""));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("nom", Operateur.AU_MOINS, "true"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("d", Operateur.AGE_AU_MOINS, "-1"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("d", Operateur.AGE_AU_MOINS, "151"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("d", Operateur.AGE_AU_MOINS, "18.5"));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("d", Operateur.AGE_AU_PLUS, "\"18\""));
        assertRefus(Motif.JEU_INVALIDE, () -> evaluer("d", Operateur.AGE_AU_PLUS, null));
        assertRefus(Motif.JEU_INVALIDE, () -> MOTEUR.evaluer(Regle.de("d", null, null), FICHE));
    }

    // --- Niveau CHAMPS ----------------------------------------------------------------------

    @Test
    void extraireRendLesValeursSimplesEtOmetLesAbsentes() {
        Map<String, Object> champs =
                MOTEUR.extraire(
                        List.of(
                                Chemin.lire("nom"),
                                Chemin.lire("enfants"),
                                Chemin.lire("boursier"),
                                Chemin.lire("date_deces"),
                                Chemin.lire("prenoms"),
                                Chemin.lire("adresse.commune")),
                        FICHE);

        assertThat(champs)
                .containsOnly(
                        Map.entry("nom", "SAWADOGO"),
                        Map.entry("enfants", 2),
                        Map.entry("boursier", true),
                        Map.entry("adresse.commune", "Koudougou"));
    }

    @Test
    void extraireRendUneListeQuandLeCheminTraverseUneListe() {
        Map<String, Object> champs =
                MOTEUR.extraire(
                        List.of(
                                Chemin.lire("diplomes.type"),
                                Chemin.lire("diplomes[type=BAC].annee"),
                                Chemin.lire("diplomes[type=MASTER].annee")),
                        FICHE);

        assertThat(champs)
                .containsOnly(
                        Map.entry("diplomes.type", List.of("BAC", "LICENCE")),
                        Map.entry("diplomes[type=BAC].annee", List.of(2015)),
                        Map.entry("diplomes[type=MASTER].annee", List.of()));
    }

    @Test
    void extraireNeSortJamaisUnObjetEntier() {
        assertRefus(
                Motif.REGLE_INAPPLICABLE,
                () -> MOTEUR.extraire(List.of(Chemin.lire("adresse")), FICHE));
        assertRefus(
                Motif.REGLE_INAPPLICABLE,
                () -> MOTEUR.extraire(List.of(Chemin.lire("diplomes")), FICHE));
    }
}
