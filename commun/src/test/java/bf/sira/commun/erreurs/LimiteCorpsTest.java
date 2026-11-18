package bf.sira.commun.erreurs;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteCorpsTest {

    private final LimiteCorps limite = new LimiteCorps(10);

    /** Un corps sans taille annoncée, comme un envoi par morceaux. */
    private static MockHttpServletRequest requete(int taille) {
        MockHttpServletRequest requete =
                new MockHttpServletRequest("POST", "/v1/demandes") {
                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }
                };
        requete.setContent(new byte[taille]);
        return requete;
    }

    @Test
    void relireLeFluxNeRemetPasLeCompteurAZero() throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        limite.doFilter(
                requete(12),
                reponse,
                (lue, rendue) -> {
                    lue.getInputStream().readNBytes(6);
                    ServletInputStream encore = lue.getInputStream();
                    encore.readNBytes(6);
                    rendue.getWriter().write("accepté");
                });

        assertThat(reponse.getStatus()).isEqualTo(413);
    }

    @Test
    void unCorpsDansLaBorneEstLu() throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        limite.doFilter(
                requete(10),
                reponse,
                (lue, rendue) -> {
                    lue.getInputStream().readNBytes(4);
                    lue.getInputStream().readAllBytes();
                });

        assertThat(reponse.getStatus()).isEqualTo(200);
    }
}
