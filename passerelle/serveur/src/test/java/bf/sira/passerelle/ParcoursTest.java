package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Les trois canaux d'accord, de la demande du service aux attestations qu'il ouvre. */
class ParcoursTest extends PasserelleDeTest {

    private static final List<String> JEUX_CONCOURS =
            List.of("casier.neant", "etat-civil.majeur", "nationalite.burkinabe");

    @Test
    void notificationPuisReutilisationSansNouvelEcran() throws Exception {
        String personne = nouvellePersonne();

        JsonNode creee = notifier(personne);
        assertThat(creee.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(creee.path("code_verification").asString()).matches("[0-9]{6}");
        assertThat(creee.path("code_court").isNull()).isTrue();
        assertThat(valeurs(creee.path("jeux"), "code"))
                .containsExactlyInAnyOrderElementsOf(JEUX_CONCOURS);
        String id = creee.path("id").asString();

        JsonNode enAttente =
                reussi(lire(citoyen(personne), "/v1/citoyen/demandes?etat=EN_ATTENTE_ACCORD"), 200);
        assertThat(enAttente.path("total").asInt()).isEqualTo(1);
        JsonNode vue = enAttente.path("elements").get(0);
        assertThat(vue.path("id").asString()).isEqualTo(id);
        // Le code n'est pas rendu au citoyen : il doit le lire sur l'écran du service et le taper.
        assertThat(vue.has("code_verification")).isFalse();
        assertThat(vue.path("code_verification_exige").asBoolean()).isTrue();
        assertThat(vue.path("service").path("code").asString()).isEqualTo("concours");
        assertThat(vue.path("demarche").path("duree_autorisation_jours").asInt()).isEqualTo(30);
        assertThat(vue.path("jeux").get(0).path("registre").path("nom").asString()).isNotBlank();

        JsonNode decision = decider(personne, id, "ACCEPTER");
        assertThat(decision.path("retour_url").isNull()).isTrue();

        JsonNode suivi = suivre(CONCOURS, id);
        assertThat(suivi.path("etat").asString()).isEqualTo("COMPLETE");
        String pseudonyme = suivi.path("pseudonyme").asString();
        assertThat(pseudonyme).isNotBlank().doesNotContain(personne);
        for (String jeu : JEUX_CONCOURS) {
            JsonNode element = element(suivi, jeu);
            assertThat(element.path("etat").asString()).isEqualTo("OBTENU");
            JWTClaimsSet attestation = ouvrir(CHIFFREMENT_CONCOURS, element);
            assertThat(attestation.getSubject()).isEqualTo(pseudonyme);
            assertThat(attestation.getAudience()).containsExactly(CONCOURS.uri());
            assertThat(attestation.getStringClaim("jeu")).isEqualTo(jeu);
            assertThat(attestation.getJSONObjectClaim("resultat")).containsEntry("valeur", true);
        }

        // Le registre a reçu l'identifiant unique, la finalité et le demandeur ; rien d'autre ne
        // l'a vu.
        JWTClaimsSet requete = ETAT_CIVIL.recues(personne).getFirst();
        assertThat(requete.getIssuer()).isEqualTo(IdentiteMembre.PASSERELLE.uri());
        assertThat(requete.getJSONObjectClaim("demandeur")).containsEntry("membre", CONCOURS.uri());
        assertThat(requete.getJSONObjectClaim("sujet")).containsEntry("pseudonyme", pseudonyme);
        assertThat(ETAT_CIVIL.appelants()).containsOnly(IdentiteMembre.PASSERELLE.uri());
        assertThat(
                        sql.sql("SELECT count(*) FROM journal WHERE journal::text LIKE ?")
                                .param("%" + personne + "%")
                                .query(Long.class)
                                .single())
                .isZero();

        // Par identifiant unique, qu'un navigateur anonyme peut taper, la demande repasse
        // toujours par le citoyen, même avec une autorisation active.
        String seconde = notifier(personne).path("id").asString();
        assertThat(suivre(CONCOURS, seconde).path("etat").asString())
                .isEqualTo("EN_ATTENTE_ACCORD");
        decider(personne, seconde, "REFUSER");

        // Tant que l'autorisation est active, le service qui désigne le citoyen par le pseudonyme
        // reçu (poste d'agent) n'a pas de nouvel écran, ni de nouvel appel au registre.
        JsonNode troisieme =
                creer(
                        CONCOURS,
                        avec(
                                demande("concours-direct", "NOTIFICATION", CHIFFREMENT_CONCOURS),
                                "pseudonyme",
                                pseudonyme));
        assertThat(troisieme.path("etat").asString()).isEqualTo("COMPLETE");
        assertThat(troisieme.path("code_verification").isNull()).isTrue();
        JsonNode suiviTroisieme = suivre(CONCOURS, troisieme.path("id").asString());
        assertThat(valeurs(suiviTroisieme.path("elements"), "etat")).containsOnly("REUTILISE");
        assertThat(element(suiviTroisieme, "casier.neant").path("attestation").asString())
                .isEqualTo(element(suivi, "casier.neant").path("attestation").asString());
        assertThat(ETAT_CIVIL.recues(personne)).hasSize(1);
        assertThat(CASIER.recues(personne)).hasSize(1);

        JsonNode journal = journalDe(personne);
        assertThat(valeurs(journal, "type"))
                .contains("DEMANDE", "ACCORD", "CONSULTATION", "REUTILISATION");
        JsonNode consultation =
                journal.valueStream()
                        .filter(l -> "CONSULTATION".equals(l.path("type").asString()))
                        .findFirst()
                        .orElseThrow();
        assertThat(consultation.path("resultat").asString()).isEqualTo("REUSSI");
        assertThat(consultation.path("service").path("nom").asString()).isNotBlank();
        assertThat(consultation.path("finalite").asString()).isNotBlank();
        assertThat(consultation.path("id_echange").asString()).isNotBlank();

        JsonNode autorisations =
                reussi(lire(citoyen(personne), "/v1/citoyen/autorisations?etat=ACTIVE"), 200);
        assertThat(autorisations.path("total").asInt()).isEqualTo(1);
        assertThat(autorisations.path("elements").get(0).path("consultations").asInt())
                .isGreaterThanOrEqualTo(3);
        JsonNode resume = reussi(lire(citoyen(personne), "/v1/citoyen/resume"), 200);
        assertThat(resume.path("autorisations_actives").asInt()).isEqualTo(1);
        assertThat(resume.path("demandes_en_attente").asInt()).isZero();
        assertThat(resume.path("consultations_30_jours").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void codeCourtPuisUsageUnique() throws Exception {
        String personne = nouvellePersonne();

        JsonNode creee = creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
        String id = creee.path("id").asString();
        String codeCourt = creee.path("code_court").asString();
        assertThat(codeCourt).matches("[A-Z]{4}-[0-9]{4}");
        assertThat(creee.path("url_accord").asString())
                .isEqualTo("https://citoyen.exemple.bf/accord/" + id);

        // Le citoyen tape le code comme il le lit : casse et tiret sont indifférents.
        JsonNode rattachee =
                reussi(
                        envoyer(
                                citoyen(personne),
                                "/v1/citoyen/demandes/rattachement",
                                Map.of("code_court", codeCourt.toLowerCase().replace("-", " "))),
                        200);
        assertThat(rattachee.path("id").asString()).isEqualTo(id);
        assertThat(creee.path("code_verification").asString()).matches("[0-9]{6}");
        assertThat(rattachee.path("code_verification_exige").asBoolean()).isTrue();
        assertThat(rattachee.path("service").path("code").asString()).isEqualTo("bourses");
        assertThat(rattachee.path("jeux").size()).isEqualTo(4);

        JsonNode avant = suivre(BOURSES, id);
        assertThat(avant.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(avant.path("pseudonyme").isNull()).isTrue();

        decider(personne, id, "ACCEPTER");
        JsonNode suivi = suivre(BOURSES, id);
        assertThat(suivi.path("etat").asString()).isEqualTo("COMPLETE");
        assertThat(valeurs(suivi.path("elements"), "etat")).hasSize(4).containsOnly("OBTENU");
        assertThat(
                        ouvrir(CHIFFREMENT_BOURSES, element(suivi, "diplomes.attestation"))
                                .getJSONObjectClaim("resultat"))
                .containsKey("document");
        assertThat(
                        ouvrir(CHIFFREMENT_BOURSES, element(suivi, "etat-civil.identite"))
                                .getJSONObjectClaim("resultat"))
                .containsKey("champs");
        // Un jeu de niveau CHAMPS ne demande au registre que les champs déclarés.
        Map<String, Object> jeu = ETAT_CIVIL.recues(personne).getFirst().getJSONObjectClaim("jeu");
        assertThat(jeu).containsEntry("niveau", "CHAMPS").containsKey("champs");

        // Durée 0 : l'accord n'a servi qu'une fois ; même désigné par son pseudonyme, le citoyen
        // revoit la demande suivante.
        JsonNode suivante =
                creer(
                        BOURSES,
                        avec(
                                demande("bourse-etudes", "NOTIFICATION", CHIFFREMENT_BOURSES),
                                "pseudonyme",
                                suivi.path("pseudonyme").asString()));
        assertThat(suivante.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(
                        reussi(
                                        lire(
                                                citoyen(personne),
                                                "/v1/citoyen/autorisations?etat=ACTIVE"),
                                        200)
                                .path("total")
                                .asInt())
                .isZero();
    }

    @Test
    void redirectionAvecCodeDeRetour() {
        String personne = nouvellePersonne();

        JsonNode creee =
                creer(
                        CONCOURS,
                        avec(
                                demande("concours-direct", "REDIRECTION", CHIFFREMENT_CONCOURS),
                                "retour_url",
                                RETOUR_CONCOURS));
        String id = creee.path("id").asString();
        assertThat(creee.path("url_accord").asString()).endsWith("/accord/" + id);
        assertThat(creee.path("code_court").isNull()).isTrue();
        assertThat(creee.path("code_verification").isNull()).isTrue();

        reussi(
                envoyer(citoyen(personne), "/v1/citoyen/demandes/" + id + "/rattachement", null),
                200);
        String retour = decider(personne, id, "ACCEPTER").path("retour_url").asString();
        assertThat(retour).startsWith(RETOUR_CONCOURS + "?");
        var parametres = UriComponentsBuilder.fromUriString(retour).build().getQueryParams();
        assertThat(parametres.getFirst("demande")).isEqualTo(id);
        String codeRetour = parametres.getFirst("code_retour");

        // Sans le code remis au navigateur du citoyen, le service ne lit que l'état.
        attendre(
                Duration.ofSeconds(10),
                "demande complète",
                () -> "COMPLETE".equals(suivre(CONCOURS, id).path("etat").asString()));
        JsonNode sansCode = suivre(CONCOURS, id);
        assertThat(sansCode.path("pseudonyme").isNull()).isTrue();
        assertThat(sansCode.path("elements").valueStream().noneMatch(e -> e.has("attestation")))
                .isTrue();
        assertRefus(
                lire(membre(CONCOURS), "/v1/demandes/" + id + "?code_retour=" + "A".repeat(43)),
                403,
                "CODE_INVALIDE");

        JsonNode avecCode =
                reussi(
                        lire(membre(CONCOURS), "/v1/demandes/" + id + "?code_retour=" + codeRetour),
                        200);
        assertThat(avecCode.path("pseudonyme").asString()).isNotBlank();
        assertThat(valeurs(avecCode.path("elements"), "etat")).hasSize(3).containsOnly("OBTENU");
        assertThat(avecCode.path("elements").valueStream().allMatch(e -> e.has("attestation")))
                .isTrue();
    }

    @Test
    void refusDuCitoyenRienNEstDemandeAuxRegistres() {
        String personne = nouvellePersonne();
        String id = notifier(personne).path("id").asString();

        assertThat(decider(personne, id, "REFUSER").path("etat").asString()).isEqualTo("REFUSEE");

        JsonNode suivi = suivre(CONCOURS, id);
        assertThat(suivi.path("etat").asString()).isEqualTo("REFUSEE");
        assertThat(suivi.path("elements").size()).isZero();
        assertThat(suivi.path("pseudonyme").isNull()).isTrue();
        assertThat(ETAT_CIVIL.recues(personne)).isEmpty();
        assertThat(valeurs(journalDe(personne), "type")).contains("REFUS");
    }

    @Test
    void annulationParLeService() {
        String personne = nouvellePersonne();
        String id = notifier(personne).path("id").asString();

        reussi(supprimer(membre(CONCOURS), "/v1/demandes/" + id), 204);

        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("ANNULEE");
        assertThat(
                        envoyer(
                                        citoyen(personne),
                                        "/v1/citoyen/demandes/" + id + "/decision",
                                        Map.of("decision", "ACCEPTER"))
                                .getStatusCode()
                                .is4xxClientError())
                .isTrue();
        assertThat(ETAT_CIVIL.recues(personne)).isEmpty();
    }

    @Test
    void lePseudonymeChangeDUnServiceALAutre() {
        String personne = nouvellePersonne();
        String chezConcours = suivre(CONCOURS, accordee(personne)).path("pseudonyme").asString();

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
        String chezBourses = suivre(BOURSES, id).path("pseudonyme").asString();

        assertThat(chezBourses).isNotBlank().isNotEqualTo(chezConcours);
        // Un pseudonyme ne vaut que chez le service qui l'a reçu.
        assertRefus(
                creer(
                        BOURSES,
                        java.util.UUID.randomUUID().toString(),
                        avec(
                                demande("bourse-etudes", "NOTIFICATION", CHIFFREMENT_BOURSES),
                                "pseudonyme",
                                chezConcours)),
                404,
                "CITOYEN_INCONNU");
    }
}
