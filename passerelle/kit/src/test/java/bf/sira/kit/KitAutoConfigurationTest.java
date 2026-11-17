package bf.sira.kit;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.PkiDeTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Un service qui ajoute le kit à ses dépendances reçoit un {@link ClientSira} prêt à l'emploi. */
@SpringBootTest(properties = "sira.mtls.mode=client")
class KitAutoConfigurationTest {

    private static final Path REPERTOIRE = repertoire();

    @Autowired private ApplicationContext contexte;

    private static Path repertoire() {
        try {
            return PkiDeTest.creer()
                    .ecrire(
                            Files.createTempDirectory("sira-pki"),
                            IdentiteMembre.service("concours"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("sira.pki.repertoire", REPERTOIRE::toString);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Appli {}

    @Test
    void leClientEstFourniAvecLesClesDuService() {
        assertThat(contexte.getBean(ClientSira.class)).isNotNull();
    }
}
