package bf.sira.demo.service.dossiers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomDeFichierTest {

    @Test
    void leNomGardeNeContientNiCheminNiCaractereDangereux() {
        assertThat(Declarations.nomDeFichier("cv.pdf")).isEqualTo("cv.pdf");
        assertThat(Declarations.nomDeFichier("Relevé de notes (2025).PDF"))
                .isEqualTo("Relevé de notes (2025).pdf");
        assertThat(Declarations.nomDeFichier("../../etc/passwd")).isEqualTo("passwd.pdf");
        assertThat(Declarations.nomDeFichier("C:\\Users\\a\\cv.pdf")).isEqualTo("cv.pdf");
        assertThat(Declarations.nomDeFichier("cv\r\nX-Entete: oui.pdf"))
                .isEqualTo("cv_X-Entete_ oui.pdf");
        assertThat(Declarations.nomDeFichier(".htaccess")).isEqualTo("htaccess.pdf");
        assertThat(Declarations.nomDeFichier("<script>.html")).isEqualTo("script_.html.pdf");
        assertThat(Declarations.nomDeFichier(null)).isEqualTo("piece.pdf");
        assertThat(Declarations.nomDeFichier("///")).isEqualTo("piece.pdf");
        assertThat(Declarations.nomDeFichier("a".repeat(300) + ".pdf")).hasSize(84);
    }
}
