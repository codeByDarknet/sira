package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.journal.PointsDeControle;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;

/** Le journal : en ajout seul, chaîné, certifié, lu par le citoyen et par l'administration. */
class JournalTest extends PasserelleDeTest {

    @Autowired PointsDeControle pointsDeControle;

    private JsonNode verification() {
        return reussi(lire(console("issa", "auditeur"), "/v1/admin/journal/verification"), 200);
    }

    @Test
    void laBaseRefuseDeModifierOuDEffacerUneLigne() {
        accordee(nouvellePersonne());

        assertThatThrownBy(() -> sql.sql("UPDATE journal SET service = 'autre'").update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> sql.sql("DELETE FROM journal").update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> sql.sql("TRUNCATE journal CASCADE").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void uneLigneFalsifieeEstDetecteeALaVerification() {
        accordee(nouvellePersonne());
        pointsDeControle.produire();
        accordee(nouvellePersonne());

        JsonNode saine = verification();
        assertThat(saine.path("valide").asBoolean()).isTrue();
        assertThat(saine.path("premiere_rupture").isNull()).isTrue();
        assertThat(saine.path("dernier_hachage").asString()).matches("[0-9a-f]{64}");
        JsonNode point = saine.path("dernier_point_de_controle");
        assertThat(point.path("jws").asString()).contains(".");
        long lignes = saine.path("lignes").asLong();
        long certifiee = point.path("ligne").asLong();
        assertThat(certifiee).isPositive().isLessThan(lignes);

        // Quelqu'un qui aurait la main sur la base et lèverait la protection : la chaîne le trahit.
        long falsifiee = certifiee - 1;
        String finalite =
                sql.sql("SELECT finalite FROM journal WHERE numero = ?")
                        .param(falsifiee)
                        .query(String.class)
                        .single();
        sql.sql("ALTER TABLE journal DISABLE TRIGGER journal_sans_modification").update();
        try {
            sql.sql("UPDATE journal SET finalite = 'Autre finalité' WHERE numero = ?")
                    .param(falsifiee)
                    .update();
            JsonNode rompue = verification();
            assertThat(rompue.path("valide").asBoolean()).isFalse();
            assertThat(rompue.path("premiere_rupture").asLong()).isEqualTo(falsifiee);
        } finally {
            sql.sql("UPDATE journal SET finalite = ? WHERE numero = ?")
                    .params(finalite, falsifiee)
                    .update();
            sql.sql("ALTER TABLE journal ENABLE TRIGGER journal_sans_modification").update();
        }
        assertThat(verification().path("valide").asBoolean()).isTrue();
    }

    @Test
    void leCitoyenNeLitEtNeSignaleQueSesLignes() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        accordee(personne);
        accordee(autre);

        JsonNode lignes = journalDe(personne);
        assertThat(lignes.size()).isEqualTo(journalDe(autre).size()).isGreaterThanOrEqualTo(5);
        assertThat(
                        reussi(
                                        lire(
                                                citoyen(personne),
                                                "/v1/citoyen/journal?type=CONSULTATION&service=concours"),
                                        200)
                                .path("total")
                                .asInt())
                .isEqualTo(3);
        String ligne =
                lignes.valueStream()
                        .filter(l -> "CONSULTATION".equals(l.path("type").asString()))
                        .findFirst()
                        .orElseThrow()
                        .path("id")
                        .asString();
        Map<String, String> motif = Map.of("motif", "Je n'ai jamais fait cette démarche.");
        String chemin = "/v1/citoyen/journal/" + ligne + "/signalement";

        assertRefus(envoyer(citoyen(autre), chemin, motif), 404, null);
        assertRefus(envoyer(citoyen(personne), chemin, Map.of("motif", "court")), 400, null);
        reussi(envoyer(citoyen(personne), chemin, motif), 201);
        assertRefus(envoyer(citoyen(personne), chemin, motif), 409, null);
        assertThat(
                        journalDe(personne)
                                .valueStream()
                                .filter(l -> ligne.equals(l.path("id").asString()))
                                .findFirst()
                                .orElseThrow()
                                .path("signalee")
                                .asBoolean())
                .isTrue();

        // L'administration traite le signalement sans jamais lire qui est le citoyen.
        var signalements = lire(console("issa", "auditeur"), "/v1/admin/signalements?taille=100");
        assertThat(signalements.getBody()).doesNotContain(personne);
        String signalement =
                reussi(signalements, 200)
                        .path("elements")
                        .valueStream()
                        .filter(s -> s.toString().contains(ligne))
                        .findFirst()
                        .orElseThrow()
                        .path("id")
                        .asString();
        Map<String, String> reponse = Map.of("reponse", "Accès vérifié auprès du service.");
        String traitement = "/v1/admin/signalements/" + signalement + "/traitement";
        assertRefus(envoyer(console("issa", "auditeur"), traitement, reponse), 403, null);
        reussi(envoyer(console("awa", "instructeur"), traitement, reponse), 204);
        assertRefus(envoyer(console("awa", "instructeur"), traitement, reponse), 409, null);

        var journal =
                lire(console("issa", "auditeur"), "/v1/admin/journal?service=concours&taille=100");
        assertThat(journal.getBody()).doesNotContain(personne).doesNotContain(autre);
        JsonNode premiere = reussi(journal, 200).path("elements").get(0);
        assertThat(premiere.path("service").path("code").asString()).isEqualTo("concours");
        assertThat(premiere.path("pseudonyme").asString()).hasSizeLessThanOrEqualTo(12);
    }
}
