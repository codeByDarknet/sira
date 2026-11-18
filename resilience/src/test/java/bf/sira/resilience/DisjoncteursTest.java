package bf.sira.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** La protection d'un registre, sans base ni réseau. */
class DisjoncteursTest {

    private final Disjoncteurs disjoncteurs =
            new Disjoncteurs(
                    new ReglageDisjoncteur(4, 4, 50, Duration.ofMinutes(1), 1),
                    new ReglageEssais(1, Duration.ZERO, 10),
                    mock(JdbcClient.class, RETURNS_DEEP_STUBS),
                    Clock.systemUTC());

    @Test
    void desPannesRepeteesOuvrentLeDisjoncteur() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> disjoncteurs.appeler("ETAT_CIVIL", false, this::panne))
                    .isInstanceOf(ConnecteurInjoignable.class);
        }
        assertThat(disjoncteurs.etat("ETAT_CIVIL")).isEqualTo(Disjoncteurs.Etat.OUVERT);
        assertThat(disjoncteurs.ouverts()).containsExactly("ETAT_CIVIL");
        assertThatThrownBy(() -> disjoncteurs.appeler("ETAT_CIVIL", false, () -> "réponse"))
                .isInstanceOf(CallNotPermittedException.class);
        // Chaque registre a son propre disjoncteur.
        assertThat(disjoncteurs.appeler("IMPOTS", false, () -> "réponse")).isEqualTo("réponse");
    }

    @Test
    void unRefusDuRegistreNEstPasUnePanne() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() ->
                            disjoncteurs.appeler(
                                    "ETAT_CIVIL",
                                    false,
                                    () -> {
                                        throw new IllegalArgumentException("refus");
                                    }))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(disjoncteurs.etat("ETAT_CIVIL")).isEqualTo(Disjoncteurs.Etat.FERME);
    }

    private String panne() {
        throw new ConnecteurInjoignable("registre éteint");
    }
}
