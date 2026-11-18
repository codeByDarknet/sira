package bf.sira.passerelle.referentiel;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.erreurs.SaisieRefusee;
import java.util.List;
import org.junit.jupiter.api.Test;

class JeuxTest {

    private static final Jeu.Regle MAJEUR =
            new Jeu.Regle("date_naissance", Operateur.AGE_AU_MOINS, null);

    private static Jeu jeu(
            Niveau niveau, String validite, Jeu.Regle regle, List<String> champs, String document) {
        return new Jeu(
                "etat-civil.essai",
                "etat-civil",
                niveau,
                "Essai",
                null,
                validite,
                regle,
                champs,
                document,
                true);
    }

    @Test
    void chaqueNiveauPorteSaFormeEtRienDAutre() {
        assertThatCode(() -> Jeux.verifierForme(jeu(Niveau.STATUT, "P90D", MAJEUR, null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                        Jeux.verifierForme(jeu(Niveau.CHAMPS, "PT1H", null, List.of("nom"), null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> Jeux.verifierForme(jeu(Niveau.DOCUMENT, "P1D", null, null, "diplome")))
                .doesNotThrowAnyException();

        // Un jeu STATUT qui porterait aussi des champs ferait sortir plus que la réponse oui/non.
        assertThatThrownBy(() ->
                        Jeux.verifierForme(
                                jeu(Niveau.STATUT, "P90D", MAJEUR, List.of("nom"), null)))
                .isInstanceOf(SaisieRefusee.class);
        assertThatThrownBy(() -> Jeux.verifierForme(jeu(Niveau.STATUT, "P90D", null, null, null)))
                .isInstanceOf(SaisieRefusee.class);
        assertThatThrownBy(() -> Jeux.verifierForme(jeu(Niveau.CHAMPS, "P90D", MAJEUR, null, null)))
                .isInstanceOf(SaisieRefusee.class);
        assertThatThrownBy(() ->
                        Jeux.verifierForme(
                                jeu(Niveau.DOCUMENT, "P90D", null, List.of("nom"), "diplome")))
                .isInstanceOf(SaisieRefusee.class);
    }

    @Test
    void laValiditeEstUneDureeBornee() {
        for (String validite : new String[] {"bientôt", "PT0S", "-P1D", "P400D"}) {
            assertThatThrownBy(() ->
                            Jeux.verifierForme(jeu(Niveau.STATUT, validite, MAJEUR, null, null)))
                    .isInstanceOf(SaisieRefusee.class);
        }
    }
}
