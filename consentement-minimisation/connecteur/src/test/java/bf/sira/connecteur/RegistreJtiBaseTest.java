package bf.sira.connecteur;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.jose.RegistreJti;
import bf.sira.connecteur.rejeu.RegistreJtiBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RegistreJtiBaseTest extends ConnecteurDeTest {

    @Autowired RegistreJti registre;

    @Test
    void laMemoireDesRequetesEstCelleDeLaBase() {
        assertThat(registre).isInstanceOf(RegistreJtiBase.class);
    }

    @Test
    void unIdentifiantNeSertQuUneFois() {
        String cle = "urn:sira:socle:passerelle|" + UUID.randomUUID();
        Instant echeance = Instant.now().plusSeconds(60);

        assertThat(registre.consommer(cle, echeance)).isTrue();
        assertThat(registre.consommer(cle, echeance)).isFalse();
    }

    @Test
    void lesIdentifiantsEchusSontOublies() {
        String cle = "urn:sira:socle:passerelle|" + UUID.randomUUID();
        registre.consommer(cle, Instant.now().minusSeconds(1));

        registre.consommer("autre|" + UUID.randomUUID(), Instant.now().plusSeconds(60));

        assertThat(
                        jdbc.sql("SELECT count(*) FROM jti_vu WHERE cle = :cle")
                                .param("cle", cle)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    void sansMemoireLaRequeteEstRefusee() {
        // Une clé plus longue que la colonne : l'écriture échoue, donc rien n'est accepté.
        assertThat(registre.consommer("x".repeat(400), Instant.now().plusSeconds(60))).isFalse();
    }
}
