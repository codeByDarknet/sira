package bf.sira.commun.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import bf.sira.commun.jetons.JetonsRoyaume;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.pki.Pki;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SiraAutoConfigurationTest {

    private final ApplicationContextRunner contexte =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SiraAutoConfiguration.class))
                    .withPropertyValues("sira.mtls.mode=client")
                    .withBean(Pki.class, () -> mock(Pki.class))
                    .withBean(ClientsMtls.class, () -> mock(ClientsMtls.class));

    @Test
    void unServeurDeRessourcesRecoitLaValidationDesJetons() {
        contexte.run(c -> assertThat(c).hasNotFailed().hasSingleBean(JetonsRoyaume.class));
    }

    @Test
    void unSimpleClientOidcDemarreSansLaBibliothequeDuServeurDeRessources() {
        // Le client OIDC apporte JwtDecoder, pas le serveur de ressources : rien ne doit l'exiger.
        contexte.withClassLoader(new FilteredClassLoader(JwtAuthenticationConverter.class))
                .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(JetonsRoyaume.class));
    }
}
