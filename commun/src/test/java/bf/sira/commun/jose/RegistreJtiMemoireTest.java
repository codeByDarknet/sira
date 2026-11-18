package bf.sira.commun.jose;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RegistreJtiMemoireTest {

    private static final Instant DEBUT = Instant.parse("2026-11-16T08:00:00Z");

    @Test
    void unIdentifiantNeSertQuUneFois() {
        RegistreJti registre = new RegistreJtiMemoire(10, horloge(DEBUT));

        assertThat(registre.consommer("a", DEBUT.plusSeconds(60))).isTrue();
        assertThat(registre.consommer("a", DEBUT.plusSeconds(60))).isFalse();
        assertThat(registre.consommer("b", DEBUT.plusSeconds(60))).isTrue();
    }

    @Test
    void unRegistrePleinRefusePlutotQueDOublier() {
        RegistreJti registre = new RegistreJtiMemoire(2, horloge(DEBUT));
        registre.consommer("a", DEBUT.plusSeconds(60));
        registre.consommer("b", DEBUT.plusSeconds(60));

        assertThat(registre.consommer("c", DEBUT.plusSeconds(60))).isFalse();
        assertThat(registre.consommer("a", DEBUT.plusSeconds(60))).isFalse();
    }

    @Test
    void lesIdentifiantsEchusLiberentLaPlace() {
        Instant[] maintenant = {DEBUT};
        Clock horloge =
                new Clock() {
                    @Override
                    public Instant instant() {
                        return maintenant[0];
                    }

                    @Override
                    public java.time.ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }
                };
        RegistreJti registre = new RegistreJtiMemoire(2, horloge);
        registre.consommer("a", DEBUT.plusSeconds(60));
        registre.consommer("b", DEBUT.plusSeconds(60));

        maintenant[0] = DEBUT.plusSeconds(61);

        assertThat(registre.consommer("c", DEBUT.plusSeconds(120))).isTrue();
    }

    private static Clock horloge(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
