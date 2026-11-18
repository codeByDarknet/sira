package bf.sira.commun.mtls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.test.FauxFournisseur;
import bf.sira.commun.test.PkiDeTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Un client sortant ne parle qu'au membre qu'il nomme (contrat §5.2). */
class ClientsMtlsTest {

    private static final PkiDeTest PKI = PkiDeTest.creer();
    private static final FauxFournisseur PASSERELLE =
            new FauxFournisseur(PKI, IdentiteMembre.PASSERELLE);

    private final ClientsMtls clients =
            new ClientsMtls(
                    PKI.bundle(PKI.tls(IdentiteMembre.ESPACE_CITOYEN)),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(5),
                    RestClient::builder);

    @AfterAll
    static void arreter() {
        PASSERELLE.close();
    }

    private Map<String, Object> echo(IdentiteMembre attendu) {
        return clients.client(PASSERELLE.racine(), attendu)
                .get()
                .uri("/v1/echo")
                .header("Authorization", "Bearer secret")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Test
    void leServeurAttenduEstJoint() {
        assertThat(echo(IdentiteMembre.PASSERELLE))
                .containsEntry("membre", "urn:sira:socle:espace-citoyen");
    }

    @Test
    void unAutreMembreDeLaPkiEstRefuseAvantToutEnvoi() {
        // Même autorité, même nom d'hôte : seule l'identité SAN URI diffère.
        try (FauxFournisseur imposteur = new FauxFournisseur(PKI, IdentiteMembre.registre("x"))) {
            assertThatThrownBy(() ->
                            clients.client(imposteur.racine(), IdentiteMembre.PASSERELLE)
                                    .get()
                                    .uri("/v1/echo")
                                    .header("Authorization", "Bearer secret")
                                    .retrieve()
                                    .body(Map.class))
                    .isInstanceOf(ResourceAccessException.class);
            assertThat(imposteur.dernierEnTeteAuthorization()).isNull();
            assertThat(imposteur.dernierCertificat()).isNull();
        }
    }

    @Test
    void lIdentiteAttendueEstPropreAChaqueClient() {
        assertThatThrownBy(() -> echo(IdentiteMembre.FOURNISSEUR_IDENTITE))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(echo(IdentiteMembre.PASSERELLE)).isNotEmpty();
    }
}
