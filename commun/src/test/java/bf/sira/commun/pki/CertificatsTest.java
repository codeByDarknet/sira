package bf.sira.commun.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.PkiDeTest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificatsTest {

    private static final IdentiteMembre SERVICE = IdentiteMembre.service("demo-bourse");

    private final PkiDeTest pki = PkiDeTest.creer();

    @Test
    void uneChaineEmiseParLAutoriteEstAcceptee() {
        Bicle cachet = pki.cachet(SERVICE);

        assertThatCode(() ->
                        Certificats.validerChaine(cachet.chaine(), pki.autorite(), Instant.now()))
                .doesNotThrowAnyException();
        assertThat(Certificats.uriSan(cachet.certificat()))
                .contains("urn:sira:service:demo-bourse");
    }

    @Test
    void unCertificatDUneAutreAutoriteEstRefuse() {
        Bicle etranger = PkiDeTest.creer().cachet(SERVICE);

        assertThatThrownBy(() ->
                        Certificats.validerChaine(etranger.chaine(), pki.autorite(), Instant.now()))
                .isInstanceOf(CertificatRefuse.class);
    }

    @Test
    void unCertificatExpireEstRefuse() {
        Bicle cachet = pki.cachet(SERVICE);

        assertThatThrownBy(() ->
                        Certificats.validerChaine(
                                cachet.chaine(),
                                pki.autorite(),
                                Instant.now().plus(Duration.ofDays(3))))
                .isInstanceOf(CertificatRefuse.class);
    }

    @Test
    void lAutoriteSeuleNEstPasUneChaine() {
        assertThatThrownBy(() ->
                        Certificats.validerChaine(
                                List.of(pki.autorite()), pki.autorite(), Instant.now()))
                .isInstanceOf(CertificatRefuse.class);
    }

    @Test
    void uneCleNeSertQuASonUsage() {
        assertThatCode(() ->
                        Certificats.validerUsage(pki.cachet(SERVICE).certificat(), UsageCle.CACHET))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                        Certificats.validerUsage(
                                pki.cachet(SERVICE).certificat(), UsageCle.CHIFFREMENT))
                .isInstanceOf(CertificatRefuse.class);
        assertThatThrownBy(() ->
                        Certificats.validerUsage(
                                pki.chiffrement(SERVICE).certificat(), UsageCle.CACHET))
                .isInstanceOf(CertificatRefuse.class);
        assertThatThrownBy(() ->
                        Certificats.validerUsage(pki.tls(SERVICE).certificat(), UsageCle.CACHET))
                .isInstanceOf(CertificatRefuse.class);
    }

    @Test
    void unCertificatSansKeyUsageNeVautPourAucunUsage() {
        Bicle sansUsage = pki.sansUsage(SERVICE);

        for (UsageCle usage : UsageCle.values()) {
            assertThatThrownBy(() -> Certificats.validerUsage(sansUsage.certificat(), usage))
                    .isInstanceOf(CertificatRefuse.class);
        }
    }

    @Test
    void lePemSeRelit() {
        Bicle cachet = pki.cachet(SERVICE);

        assertThat(Certificats.lirePem(Certificats.versPem(cachet.certificat())))
                .containsExactly(cachet.certificat());
        assertThatThrownBy(() -> Certificats.lirePem("pas un certificat"))
                .isInstanceOf(CertificatRefuse.class);
    }

    @Test
    void leRepertoireDUnMembreSeRelit(@TempDir Path repertoire) {
        Pki materiel = new Pki(pki.ecrire(repertoire, SERVICE));

        assertThat(materiel.autorite()).isEqualTo(pki.autorite());
        assertThat(Certificats.uriSan(materiel.cachet().certificat())).contains(SERVICE.uri());
        assertThat(Certificats.uriSan(materiel.chiffrement().certificat())).contains(SERVICE.uri());
        assertThat(Certificats.uriSan(materiel.certificatTls())).contains(SERVICE.uri());
    }
}
