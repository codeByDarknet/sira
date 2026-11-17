package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Le retrait d'une autorisation prend effet tout de suite et efface ce qui était déposé. */
class RetraitTest extends PasserelleDeTest {

    private long attestationsDeposees(String identifiantUnique) {
        return sql.sql(
                        "SELECT count(*) FROM attestation a JOIN citoyen c ON c.id = a.citoyen WHERE"
                                + " c.identifiant_unique = ?")
                .param(identifiantUnique)
                .query(Long.class)
                .single();
    }

    @Test
    void retraitImmediatAvecPurgeDesAttestations() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        String demande = accordee(personne);
        assertThat(attestationsDeposees(personne)).isEqualTo(3);
        JsonNode autorisation =
                reussi(lire(citoyen(personne), "/v1/citoyen/autorisations"), 200)
                        .path("elements")
                        .get(0);
        assertThat(autorisation.path("etat").asString()).isEqualTo("ACTIVE");
        assertThat(autorisation.path("service").path("code").asString()).isEqualTo("concours");
        assertThat(autorisation.path("jeux").size()).isEqualTo(3);
        String chemin = "/v1/citoyen/autorisations/" + autorisation.path("id").asString();

        // L'autorisation d'un autre n'existe pas : ni à lire, ni à retirer.
        assertRefus(lire(citoyen(autre), chemin), 404, null);
        assertRefus(supprimer(citoyen(autre), chemin), 404, null);
        assertThat(attestationsDeposees(personne)).isEqualTo(3);

        reussi(supprimer(citoyen(personne), chemin), 204);

        assertThat(attestationsDeposees(personne)).isZero();
        JsonNode suivi = suivre(CONCOURS, demande);
        assertThat(valeurs(suivi.path("elements"), "etat")).containsOnly("EXPIRE");
        assertThat(suivi.path("elements").valueStream().noneMatch(e -> e.has("attestation")))
                .isTrue();
        JsonNode retiree = reussi(lire(citoyen(personne), chemin), 200);
        assertThat(retiree.path("etat").asString()).isEqualTo("RETIREE");
        assertThat(retiree.path("retiree_le").asString()).isNotBlank();
        assertThat(
                        reussi(
                                        lire(
                                                citoyen(personne),
                                                "/v1/citoyen/autorisations?etat=RETIREE"),
                                        200)
                                .path("total")
                                .asInt())
                .isEqualTo(1);
        assertThat(valeurs(journalDe(personne), "type")).contains("RETRAIT");

        // Plus d'accord : la demande suivante repasse par le citoyen, et le registre est
        // réinterrogé.
        assertThat(notifier(personne).path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        reussi(supprimer(citoyen(personne), chemin), 204);
    }

    @Test
    void unRetraitPendantLaCollecteArreteLesEssaisSuivants() {
        String personne = nouvellePersonne();
        // Le premier appel au casier échoue, et le citoyen retire son accord pendant ce temps.
        CASIER.pendantLaRequete(
                personne,
                () -> {
                    String autorisation =
                            reussi(lire(citoyen(personne), "/v1/citoyen/autorisations"), 200)
                                    .path("elements")
                                    .get(0)
                                    .path("id")
                                    .asString();
                    reussi(
                            supprimer(
                                    citoyen(personne), "/v1/citoyen/autorisations/" + autorisation),
                            204);
                });
        String demande = accordee(personne);

        // Le second essai immédiat relit l'élément et le retrait : le registre n'est pas rappelé,
        // et aucune consultation n'est journalisée pour lui.
        assertThat(CASIER.recues(personne)).hasSize(1);
        assertThat(valeurs(suivre(CONCOURS, demande).path("elements"), "etat"))
                .containsOnly("EXPIRE");
        assertThat(
                        journalDe(personne)
                                .valueStream()
                                .filter(l -> l.path("type").asString().equals("CONSULTATION"))
                                .filter(l ->
                                        l.path("registre")
                                                .path("code")
                                                .asString()
                                                .equals("casier")))
                .isEmpty();
    }
}
