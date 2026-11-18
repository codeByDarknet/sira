package bf.sira.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Le chaînage du journal, sans base : chaque ligne dépend de la précédente et de son contenu. */
class ChaineTest {

    @Test
    void leMemeContenuDonneLeMemeHachage() {
        assertThat(Chaine.hachage(Chaine.ORIGINE, "ACCORD", 42, null))
                .isEqualTo(Chaine.hachage(Chaine.ORIGINE, "ACCORD", 42, null));
    }

    @Test
    void laLignePrecedenteEntreDansLeHachage() {
        byte[] premiere = Chaine.hachage(Chaine.ORIGINE, "ACCORD");
        assertThat(Chaine.hachage(premiere, "RETRAIT"))
                .isNotEqualTo(Chaine.hachage(Chaine.ORIGINE, "RETRAIT"));
    }

    @Test
    void lesColonnesNeSeConfondentPas() {
        // Chaque colonne est préfixée de sa longueur : « ab » + « c » ne vaut pas « a » + « bc ».
        assertThat(Chaine.hachage(Chaine.ORIGINE, "ab", "c"))
                .isNotEqualTo(Chaine.hachage(Chaine.ORIGINE, "a", "bc"));
        assertThat(Chaine.hachage(Chaine.ORIGINE, (Object) null))
                .isNotEqualTo(Chaine.hachage(Chaine.ORIGINE, ""));
    }

    @Test
    void lHorodatageGardeLaPrecisionDeLaBase() {
        Instant instant = Instant.parse("2026-11-17T10:00:00.123456789Z");
        assertThat(Chaine.arrondir(instant))
                .isEqualTo(Instant.parse("2026-11-17T10:00:00.123456Z"));
    }
}
