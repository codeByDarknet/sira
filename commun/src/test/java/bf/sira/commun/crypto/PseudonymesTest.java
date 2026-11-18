package bf.sira.commun.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PseudonymesTest {

    private static final byte[] CLE =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final Pseudonymes pseudonymes = new Pseudonymes(CLE);

    @Test
    void lePseudonymeEstStableEtPropreAuSecteur() {
        String chezLaBourse = pseudonymes.calculer("demo-bourse", "citoyen-1");

        assertThat(chezLaBourse).isEqualTo(pseudonymes.calculer("demo-bourse", "citoyen-1"));
        assertThat(chezLaBourse).isNotEqualTo(pseudonymes.calculer("etat-civil", "citoyen-1"));
        assertThat(chezLaBourse).isNotEqualTo(pseudonymes.calculer("demo-bourse", "citoyen-2"));
        assertThat(chezLaBourse).hasSize(43).doesNotContain("citoyen");
    }

    @Test
    void laFrontiereEntreSecteurEtIdentifiantCompte() {
        assertThat(pseudonymes.calculer("ab", "c")).isNotEqualTo(pseudonymes.calculer("a", "bc"));
    }

    @Test
    void unAutreSecretDonneUnAutrePseudonyme() {
        byte[] autre = "f".repeat(32).getBytes(StandardCharsets.UTF_8);

        assertThat(new Pseudonymes(autre).calculer("demo-bourse", "citoyen-1"))
                .isNotEqualTo(pseudonymes.calculer("demo-bourse", "citoyen-1"));
    }

    @Test
    void lesEntreesIncompletesSontRefusees() {
        assertThatThrownBy(() -> new Pseudonymes(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pseudonymes.calculer("", "citoyen-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pseudonymes.calculer("demo-bourse", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laComparaisonEnTempsConstantCompareLesValeurs() {
        assertThat(TempsConstant.egaux("code-retour", "code-retour")).isTrue();
        assertThat(TempsConstant.egaux("code-retour", "code-retouR")).isFalse();
        assertThat(TempsConstant.egaux("code", "code-retour")).isFalse();
        assertThat(TempsConstant.egaux((String) null, "code")).isFalse();
    }
}
