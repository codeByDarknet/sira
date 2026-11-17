package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * La passerelle ne remet une requête qu'au connecteur qui porte l'identité du registre visé
 * (contrat §5.2) : une adresse détournée vers un autre membre de la PKI ne reçoit rien.
 */
class IdentiteConnecteurTest extends PasserelleDeTest {

    @Test
    void unConnecteurQuiNEstPasCeluiDuRegistreNeRecoitRien() {
        String personne = nouvellePersonne();
        try (FauxConnecteur imposteur = new FauxConnecteur(PKI, "imposteur")) {
            detourner("nationalite", imposteur.adresse());
            String id;
            try {
                id =
                        creer(
                                        BOURSES,
                                        avec(
                                                demande(
                                                        "bourse-etudes",
                                                        "NOTIFICATION",
                                                        CHIFFREMENT_BOURSES),
                                                "identifiant_unique",
                                                personne))
                                .path("id")
                                .asString();
                decider(personne, id, "ACCEPTER");

                JsonNode partielle = suivre(BOURSES, id);
                assertThat(element(partielle, "nationalite.burkinabe").path("etat").asString())
                        .isEqualTo("A_TRAITER");
                assertThat(imposteur.appelants()).isEmpty();
            } finally {
                detourner("nationalite", NATIONALITE.adresse());
            }
            // Le vrai connecteur, rendu à son adresse, reçoit la requête mise en file.
            String demande = id;
            attendre(
                    Duration.ofSeconds(30),
                    "collecte auprès du vrai connecteur",
                    () -> "COMPLETE".equals(suivre(BOURSES, demande).path("etat").asString()));
            assertThat(imposteur.appelants()).isEmpty();
            assertThat(NATIONALITE.recues(personne)).hasSize(1);
        }
    }

    private void detourner(String registre, String adresse) {
        sql.sql("UPDATE registre SET adresse = ? WHERE code = ?")
                .params(adresse, registre)
                .update();
    }
}
