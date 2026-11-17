package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Un registre en panne ne bloque ni le citoyen ni les autres registres ; il est rattrapé seul. */
class ResilienceTest extends PasserelleDeTest {

    private JsonNode registre(String code) {
        return reussi(lire(console("awa", "auditeur"), "/v1/admin/etat"), 200)
                .path("registres")
                .valueStream()
                .filter(r -> code.equals(r.path("code").asString()))
                .findFirst()
                .orElseThrow();
    }

    private String demandeDeBourse(String personne) {
        String id =
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
        return id;
    }

    @Test
    void panneDUnRegistrePuisRepriseAutomatique() {
        String personne = nouvellePersonne();
        DIPLOMES.enPanne(true);

        String id = demandeDeBourse(personne);

        // Ce qui pouvait être obtenu l'est ; le reste attend dans la file, sans erreur pour
        // personne.
        JsonNode partielle = suivre(BOURSES, id);
        assertThat(partielle.path("etat").asString()).isEqualTo("PARTIELLE");
        assertThat(element(partielle, "etat-civil.identite").path("etat").asString())
                .isEqualTo("OBTENU");
        assertThat(element(partielle, "nationalite.burkinabe").path("etat").asString())
                .isEqualTo("OBTENU");
        JsonNode enFile = element(partielle, "diplomes.baccalaureat");
        assertThat(enFile.path("etat").asString()).isEqualTo("A_TRAITER");
        assertThat(enFile.path("prochaine_tentative_le").asString()).isNotBlank();
        assertThat(enFile.has("attestation")).isFalse();

        attendre(
                Duration.ofSeconds(10),
                "disjoncteur ouvert",
                () -> "OUVERT".equals(registre("diplomes").path("disjoncteur").asString()));
        JsonNode enPanne = registre("diplomes");
        assertThat(enPanne.path("en_file").asInt()).isEqualTo(2);
        assertThat(enPanne.path("dernier_echec_le").asString()).isNotBlank();
        assertThat(registre("etat-civil").path("disjoncteur").asString()).isEqualTo("FERME");
        assertThat(
                        reussi(lire(console("awa", "auditeur"), "/v1/admin/etat"), 200)
                                .path("file")
                                .path("a_traiter")
                                .asInt())
                .isGreaterThanOrEqualTo(2);
        assertThat(DIPLOMES.recues(personne)).isEmpty();

        DIPLOMES.enPanne(false);

        attendre(
                Duration.ofSeconds(30),
                "reprise de la collecte",
                () -> "COMPLETE".equals(suivre(BOURSES, id).path("etat").asString()));
        JsonNode complete = suivre(BOURSES, id);
        assertThat(valeurs(complete.path("elements"), "etat")).hasSize(4).containsOnly("OBTENU");
        assertThat(
                        ouvrir(CHIFFREMENT_BOURSES, element(complete, "diplomes.baccalaureat"))
                                .getSubject())
                .isEqualTo(complete.path("pseudonyme").asString());
        attendre(
                Duration.ofSeconds(10),
                "disjoncteur refermé",
                () -> "FERME".equals(registre("diplomes").path("disjoncteur").asString()));
        assertThat(registre("diplomes").path("en_file").asInt()).isZero();

        // Le citoyen lit la panne puis la consultation aboutie.
        JsonNode journal = journalDe(personne);
        assertThat(
                        journal.valueStream()
                                .anyMatch(l ->
                                        "ECHEC".equals(l.path("type").asString())
                                                && "EN_ATTENTE"
                                                        .equals(l.path("resultat").asString())))
                .isTrue();
        assertThat(
                        journal.valueStream()
                                .filter(l -> "CONSULTATION".equals(l.path("type").asString()))
                                .filter(l -> "REUSSI".equals(l.path("resultat").asString()))
                                .count())
                .isEqualTo(4);
    }

    @Test
    void personneInconnueOuQuestionRefuseeNeSontPasRetentees() {
        String inconnue = nouvellePersonne();
        ETAT_CIVIL.ignore(inconnue);
        JsonNode suivi = suivre(CONCOURS, accordee(inconnue));
        assertThat(suivi.path("etat").asString()).isEqualTo("COMPLETE");
        assertThat(element(suivi, "etat-civil.majeur").path("etat").asString())
                .isEqualTo("INCONNU");
        assertThat(element(suivi, "etat-civil.majeur").has("attestation")).isFalse();
        assertThat(element(suivi, "casier.neant").path("etat").asString()).isEqualTo("OBTENU");

        String refusee = nouvellePersonne();
        CASIER.refuse(refusee);
        JsonNode autre = suivre(CONCOURS, accordee(refusee));
        assertThat(autre.path("etat").asString()).isEqualTo("COMPLETE");
        assertThat(element(autre, "casier.neant").path("etat").asString()).isEqualTo("REFUSE");

        // Une réponse du registre, même négative, n'est pas une panne.
        assertThat(registre("etat-civil").path("disjoncteur").asString()).isEqualTo("FERME");
        assertThat(registre("casier").path("disjoncteur").asString()).isEqualTo("FERME");
        assertThat(valeurs(journalDe(refusee), "resultat")).contains("REFUSE");
    }
}
