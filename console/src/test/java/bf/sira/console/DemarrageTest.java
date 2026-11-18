package bf.sira.console;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.PkiDeTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Le serveur réellement démarré, avec sa configuration livrée : ports, cookie, sonde. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class DemarrageTest {

    private static final Path CLES = cles();

    @Value("${local.server.port}")
    private int port;

    @Value("${local.management.port}")
    private int portGestion;

    private final HttpClient client = HttpClient.newHttpClient();

    private static Path cles() {
        try {
            return PkiDeTest.creer()
                    .ecrire(Files.createTempDirectory("sira-pki"), IdentiteMembre.CONSOLE);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", CLES::toString);
    }

    private HttpResponse<String> lire(int surPort, String chemin) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + surPort + chemin)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void laSondeDeSanteRepondSansSessionSurLePortDeGestion() throws Exception {
        assertThat(lire(portGestion, "/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(lire(portGestion, "/actuator/env").statusCode()).isIn(401, 404);
    }

    @Test
    void rienNEstOuvertHorsDeLApi() throws Exception {
        assertThat(lire(port, "/").statusCode()).isEqualTo(401);
        assertThat(lire(port, "/actuator/health").statusCode()).isEqualTo(404);
    }

    @Test
    void leDepartEnConnexionVaAuRoyaumeDuSocleEtPoseUnCookieProtege() throws Exception {
        HttpResponse<String> depart = lire(port, "/api/auth/connexion/sira");

        assertThat(depart.statusCode()).isEqualTo(302);
        assertThat(depart.headers().firstValue("Location").orElseThrow())
                .startsWith(
                        "http://identite.sira.localhost:8600/realms/sira-socle"
                                + "/protocol/openid-connect/auth?")
                .contains("client_id=console")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=http://console.sira.localhost:8600/api/auth/retour");
        assertThat(depart.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie ->
                        assertThat(cookie)
                                .startsWith("SIRA_CONSOLE=")
                                .contains("HttpOnly")
                                .contains("SameSite=Lax"));
    }
}
