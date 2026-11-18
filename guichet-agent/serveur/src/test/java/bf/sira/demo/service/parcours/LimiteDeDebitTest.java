package bf.sira.demo.service.parcours;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.demo.service.configuration.ProprietesService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LimiteDeDebitTest {

    private final AtomicReference<Instant> maintenant =
            new AtomicReference<>(Instant.parse("2026-11-16T10:00:00Z"));
    private final Clock horloge =
            new Clock() {
                @Override
                public Instant instant() {
                    return maintenant.get();
                }

                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }
            };
    private final LimiteDeDebit limite =
            new LimiteDeDebit(
                    new ProprietesService(
                            "concours",
                            "Concours",
                            "Ministère",
                            "concours-direct",
                            "CD",
                            Duration.ofSeconds(10),
                            2,
                            3,
                            Map.of()),
                    horloge);

    @Test
    void unNavigateurEstBorneSansGenerLesAutres() {
        assertThat(limite.admettre("a")).isTrue();
        assertThat(limite.admettre("a")).isTrue();

        assertThat(limite.admettre("a")).isFalse();
        assertThat(limite.admettre("b")).isTrue();
    }

    @Test
    void changerDeSessionNeContournePasLePlafondDeLaPlateforme() {
        assertThat(limite.admettre("a")).isTrue();
        assertThat(limite.admettre("b")).isTrue();
        assertThat(limite.admettre("c")).isTrue();

        assertThat(limite.admettre("d")).isFalse();
    }

    @Test
    void laMinuteSuivanteToutLeMondePeutRecommencer() {
        limite.admettre("a");
        limite.admettre("a");
        assertThat(limite.admettre("a")).isFalse();

        maintenant.set(maintenant.get().plusSeconds(60));

        assertThat(limite.admettre("a")).isTrue();
    }
}
