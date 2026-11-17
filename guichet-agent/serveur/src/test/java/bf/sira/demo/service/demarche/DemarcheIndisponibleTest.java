package bf.sira.demo.service.demarche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.service.configuration.ProprietesFormulaire;
import bf.sira.demo.service.configuration.ProprietesService;
import bf.sira.kit.ClientSira;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class DemarcheIndisponibleTest {

    @Test
    void sansCopieEtSansSiraLaDemarcheEstAnnonceeIndisponible() {
        ClientSira sira = mock(ClientSira.class);
        when(sira.decrireDemarche(anyString())).thenThrow(new ResourceAccessException("coupure"));
        DemarcheControleur controleur =
                new DemarcheControleur(
                        sira,
                        new ProprietesService(
                                "concours",
                                "Concours",
                                "Ministère",
                                "concours-direct",
                                "CD",
                                Duration.ofSeconds(10),
                                5,
                                120,
                                Map.of()),
                        new ProprietesFormulaire(List.of(), Map.of()),
                        Clock.systemUTC());

        assertThatThrownBy(controleur::presenter)
                .isInstanceOfSatisfying(
                        ErreurMetier.class,
                        erreur -> assertThat(erreur.getMessage()).doesNotContain("coupure"));
    }
}
