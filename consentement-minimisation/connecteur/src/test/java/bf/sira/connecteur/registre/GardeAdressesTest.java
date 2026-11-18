package bf.sira.connecteur.registre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GardeAdressesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "10.1.2.3",
                "172.20.0.5",
                "192.168.1.1",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "224.0.0.1",
                "::1",
                "fd00::1",
                "fe80::1",
                "::ffff:127.0.0.1"
            })
    void lesAdressesInternesSontRefusees(String ip) throws Exception {
        assertThat(GardeAdresses.publique(InetAddress.getByName(ip))).isFalse();
        assertThatThrownBy(() ->
                        GardeAdresses.verifier(
                                URI.create("https://" + (ip.contains(":") ? "[" + ip + "]" : ip)),
                                List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "196.28.245.10", "2001:4860:4860::8888"})
    void lesAdressesPubliquesSontAdmises(String ip) throws Exception {
        assertThat(GardeAdresses.publique(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST"})
    void unHoteDeLaListeEstAdmisMemeInterne(String hote) {
        assertThatCode(() ->
                        GardeAdresses.verifier(
                                URI.create("http://" + hote + ":8000"), List.of("localhost")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GardeAdresses.verifier(URI.create("http://" + hote), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
