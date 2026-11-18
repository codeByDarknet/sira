package bf.sira.consentement;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.crypto.Pseudonymes;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodesTest {

    private final Codes codes = new Codes(new SecureRandom(), new Pseudonymes(cle()));

    private static byte[] cle() {
        byte[] cle = new byte[32];
        new SecureRandom().nextBytes(cle);
        return cle;
    }

    @Test
    void lesCodesOntLaFormeAnnoncee() {
        assertThat(codes.codeCourt()).matches("[A-HJ-NPR-Z]{4}-[0-9]{4}");
        assertThat(codes.codeVerification()).matches("[0-9]{6}");
        // 32 octets tirés au hasard : 43 caractères sûrs dans une adresse.
        assertThat(codes.codeRetour()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void deuxCodesDeRetourNeSeRessemblentPas() {
        Set<String> tires = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tires.add(codes.codeRetour());
        }
        assertThat(tires).hasSize(1000);
    }

    @Test
    void unCodeCourtSeTapeCommeOnVeut() {
        assertThat(Codes.normaliser("klpm 4821")).isEqualTo("KLPM4821");
        assertThat(Codes.normaliser(" KLPM-4821 ")).isEqualTo("KLPM4821");
        assertThat(codes.empreinteCodeCourt("KLPM4821"))
                .isEqualTo(codes.empreinteCodeCourt("klpm-4821"));
    }

    @Test
    void sansLaCleDeLaPasserelleUneEmpreinteNeRetrouvePasLeCode() {
        // Une simple empreinte SHA-256 d'un petit code se retrouve en essayant tous les codes.
        assertThat(codes.empreinteCodeCourt("KLPM4821"))
                .isNotEqualTo(Codes.empreinte("KLPM4821"))
                .isNotEqualTo(
                        new Codes(new SecureRandom(), new Pseudonymes(cle()))
                                .empreinteCodeCourt("KLPM4821"));
        assertThat(codes.empreinteCodeVerification("492113"))
                .isNotEqualTo(codes.empreinteCodeCourt("492113"));
    }
}
