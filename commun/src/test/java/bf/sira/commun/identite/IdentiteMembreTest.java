package bf.sira.commun.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentiteMembreTest {

    @Test
    void uneUriSiraSeLitEtSeReecrit() {
        IdentiteMembre membre = IdentiteMembre.lire("urn:sira:registre:etat-civil").orElseThrow();

        assertThat(membre).isEqualTo(IdentiteMembre.registre("etat-civil"));
        assertThat(membre.uri()).isEqualTo("urn:sira:registre:etat-civil");
        assertThat(membre.autorite()).isEqualTo(Autorites.REGISTRE);
        assertThat(IdentiteMembre.service("demo-bourse").autorite()).isEqualTo(Autorites.SERVICE);
        assertThat(IdentiteMembre.PASSERELLE.autorite()).isEqualTo(Autorites.PASSERELLE);
        assertThat(IdentiteMembre.ESPACE_CITOYEN.autorite()).isEqualTo(Autorites.ESPACE_CITOYEN);
        assertThat(IdentiteMembre.CONSOLE.autorite()).isEqualTo(Autorites.CONSOLE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "urn:sira:service:",
                "urn:sira:admin:passerelle",
                "URN:SIRA:service:demo",
                "urn:sira:service:Demo",
                "urn:sira:service:demo:autre",
                "urn:sira:service:demo\n",
                " urn:sira:service:demo",
                "urn:sira:service:-demo",
                "https://exemple.bf/urn:sira:service:demo"
            })
    void touteAutreFormeEstRejetee(String uri) {
        assertThat(IdentiteMembre.lire(uri)).isEmpty();
    }

    @Test
    void unCodeInvalideNeFaitPasUneIdentite() {
        assertThat(IdentiteMembre.lire(null)).isEmpty();
        assertThatThrownBy(() -> IdentiteMembre.service("Demo Bourse"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
