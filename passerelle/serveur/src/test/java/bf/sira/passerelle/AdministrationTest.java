package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;

import bf.sira.commun.identite.IdentiteMembre;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Le référentiel administré par la console : rôles, séparation des décisions, effet immédiat. */
class AdministrationTest extends PasserelleDeTest {

    private static final IdentiteMembre IMPOTS = IdentiteMembre.service("impots");

    private RestClient instructrice() {
        return console("awa", "instructeur");
    }

    private RestClient autorite() {
        return console("moussa", "autorite");
    }

    private RestClient auditeur() {
        return console("issa", "auditeur");
    }

    private static Map<String, Object> jeuDEssai() {
        return Map.of(
                "code", "etat-civil.vivant",
                "registre", "etat-civil",
                "niveau", "STATUT",
                "libelle", "Vous êtes en vie",
                "validite", "P30D",
                "regle", Map.of("champ", "date_deces", "operateur", "AUCUN"));
    }

    @Test
    void uneHabilitationSePrepareSeDecideEtSeSuspendParDesPersonnesDistinctes() {
        Map<String, Object> membre =
                Map.of(
                        "code", "impots",
                        "nom", "Quitus fiscal",
                        "organisme", "Direction générale des impôts",
                        "roles", List.of("SERVICE"));
        Map<String, Object> demarche =
                Map.of(
                        "code", "quitus-fiscal",
                        "libelle", "Demande de quitus fiscal",
                        "finalite", "Vérifier votre nationalité pour délivrer le quitus.",
                        "service", "impots",
                        "duree_autorisation_jours", 0);
        Map<String, Object> habilitation =
                Map.of(
                        "service", "impots",
                        "demarche", "quitus-fiscal",
                        "jeux", List.of("nationalite.burkinabe"),
                        "justification", "Article 12 du code général des impôts.");
        Map<String, Object> corps = demande("quitus-fiscal", "CODE", PKI.chiffrement(IMPOTS));

        // Lire appartient aux trois rôles, écrire à l'instructeur seulement.
        assertRefus(envoyer(auditeur(), "/v1/admin/membres", membre), 403, null);
        assertRefus(envoyer(autorite(), "/v1/admin/membres", membre), 403, null);
        assertRefus(envoyer(console("sans-role"), "/v1/admin/membres", membre), 403, null);
        assertRefus(lire(console("sans-role"), "/v1/admin/membres"), 403, null);
        JsonNode cree = reussi(envoyer(instructrice(), "/v1/admin/membres", membre), 201);
        assertThat(cree.path("identite").asString()).isEqualTo(IMPOTS.uri());
        assertRefus(envoyer(instructrice(), "/v1/admin/membres", membre), 409, null);
        reussi(envoyer(instructrice(), "/v1/admin/demarches", demarche), 201);
        assertRefus(
                envoyer(
                        instructrice(),
                        "/v1/admin/habilitations",
                        avec(habilitation, "jeux", List.of("jeu.inexistant"))),
                400,
                "VALIDATION");
        JsonNode brouillon =
                reussi(envoyer(instructrice(), "/v1/admin/habilitations", habilitation), 201);
        assertThat(brouillon.path("etat").asString()).isEqualTo("BROUILLON");
        assertThat(brouillon.path("instruite_par").asString()).isEqualTo("awa");
        String chemin = "/v1/admin/habilitations/" + brouillon.path("id").asString();

        // Tant que rien n'est approuvé, le service n'obtient rien.
        assertRefus(
                creer(IMPOTS, UUID.randomUUID().toString(), corps), 403, "HABILITATION_ABSENTE");
        assertRefus(envoyer(autorite(), chemin + "/approbation", null), 409, null);
        assertThat(
                        reussi(envoyer(instructrice(), chemin + "/soumission", null), 200)
                                .path("etat")
                                .asString())
                .isEqualTo("SOUMISE");

        // Préparer et décider sont deux mains : ni le rôle seul, ni le cumul des rôles ne suffit.
        assertRefus(envoyer(instructrice(), chemin + "/approbation", null), 403, null);
        assertRefus(
                envoyer(console("awa", "instructeur", "autorite"), chemin + "/approbation", null),
                403,
                "SEPARATION_DES_ROLES");
        assertRefus(
                creer(IMPOTS, UUID.randomUUID().toString(), corps), 403, "HABILITATION_ABSENTE");
        JsonNode approuvee = reussi(envoyer(autorite(), chemin + "/approbation", null), 200);
        assertThat(approuvee.path("etat").asString()).isEqualTo("APPROUVEE");
        assertThat(approuvee.path("decidee_par").asString()).isEqualTo("moussa");
        assertThat(approuvee.path("decidee_le").asString()).isNotBlank();

        JsonNode creee = reussi(creer(IMPOTS, UUID.randomUUID().toString(), corps), 201);
        assertThat(valeurs(creee.path("jeux"), "code")).containsExactly("nationalite.burkinabe");

        // La suspension coupe tout de suite, y compris pour une demande déjà créée.
        assertRefus(envoyer(autorite(), chemin + "/suspension", Map.of()), 400, null);
        reussi(
                envoyer(autorite(), chemin + "/suspension", Map.of("motif", "Contrôle en cours.")),
                200);
        assertRefus(
                creer(IMPOTS, UUID.randomUUID().toString(), corps), 403, "HABILITATION_ABSENTE");
        String personne = nouvellePersonne();
        String demande = creee.path("id").asString();
        reussi(
                envoyer(
                        citoyen(personne),
                        "/v1/citoyen/demandes/rattachement",
                        Map.of("code_court", creee.path("code_court").asString())),
                200);
        ResponseEntity<String> decision =
                decision(personne, demande, "ACCEPTER", codeAffiche(demande));
        assertRefus(decision, 403, "HABILITATION_ABSENTE");
        assertThat(NATIONALITE.recues(personne)).isEmpty();

        // Chaque acte d'administration est au journal, avec son auteur.
        var journal = lire(auditeur(), "/v1/admin/journal?type=ADMINISTRATION&taille=100");
        assertThat(reussi(journal, 200).path("total").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(journal.getBody()).contains("moussa").contains("awa");
    }

    @Test
    void unObjetDuReferentielSeDesactiveSansSEffacer() {
        Map<String, Object> jeu =
                avec(jeuDEssai(), "code", "etat-civil.vivant-" + System.nanoTime());
        String chemin = "/v1/admin/jeux/" + jeu.get("code");

        assertRefus(
                envoyer(instructrice(), "/v1/admin/jeux", avec(jeu, "regle", null)),
                400,
                "VALIDATION");
        assertRefus(
                envoyer(instructrice(), "/v1/admin/jeux", avec(jeu, "validite", "bientôt")),
                400,
                "VALIDATION");
        assertRefus(
                envoyer(instructrice(), "/v1/admin/jeux", avec(jeu, "registre", "cadastre")),
                400,
                "VALIDATION");
        reussi(envoyer(instructrice(), "/v1/admin/jeux", jeu), 201);
        reussi(
                appeler(
                        instructrice(),
                        HttpMethod.PUT,
                        chemin,
                        avec(jeu, "libelle", "Vous êtes vivant")),
                200);
        assertRefus(appeler(auditeur(), HttpMethod.PUT, chemin, jeu), 403, null);
        assertRefus(supprimer(auditeur(), chemin), 403, null);

        reussi(supprimer(instructrice(), chemin), 204);

        JsonNode desactive = reussi(lire(auditeur(), chemin), 200);
        assertThat(desactive.path("actif").asBoolean()).isFalse();
        assertThat(desactive.path("libelle").asString()).isEqualTo("Vous êtes vivant");
        assertRefus(lire(auditeur(), "/v1/admin/jeux/jeu.absent"), 404, null);
        assertRefus(lire(auditeur(), "/v1/admin/jeux?taille=1000"), 400, null);
    }

    @Test
    void unJeuCouvertParUneHabilitationEstFige() {
        JsonNode habilitations =
                reussi(lire(auditeur(), "/v1/admin/habilitations?etat=APPROUVEE&taille=1"), 200);
        String code = habilitations.path("elements").get(0).path("jeux").get(0).asString();
        String chemin = "/v1/admin/jeux/" + code;
        JsonNode jeu = reussi(lire(auditeur(), chemin), 200);
        Map<String, Object> corps = JSON.convertValue(jeu, Map.class);

        // Élargir la question après l'approbation : refusé, même au mot près du libellé.
        assertRefus(
                appeler(instructrice(), HttpMethod.PUT, chemin, avec(corps, "libelle", "Autre")),
                409,
                "CONFLIT");
        assertRefus(
                appeler(
                        instructrice(),
                        HttpMethod.PUT,
                        chemin,
                        avec(avec(corps, "niveau", "DOCUMENT"), "document", "acte")),
                409,
                "CONFLIT");
        assertThat(reussi(lire(auditeur(), chemin), 200)).isEqualTo(jeu);
        reussi(appeler(instructrice(), HttpMethod.PUT, chemin, corps), 200);
    }

    @Test
    void lesAdressesDeRetourEtDeConnecteurSontDecideesParLAutorite() {
        String code = "guichet-" + System.nanoTime() % 1_000_000;
        Map<String, Object> membre =
                Map.of(
                        "code",
                        code,
                        "nom",
                        "Guichet",
                        "organisme",
                        "Mairie",
                        "roles",
                        List.of("SERVICE", "REGISTRE"),
                        "retours_autorises",
                        List.of("https://guichet.exemple.bf/retour"));
        Map<String, Object> registre =
                Map.of(
                        "code", code,
                        "nom", "Registre du guichet",
                        "membre", code,
                        "adresse", "https://guichet.exemple.bf:8443",
                        "transport", "DIRECT");
        reussi(envoyer(instructrice(), "/v1/admin/membres", membre), 201);
        reussi(envoyer(instructrice(), "/v1/admin/registres", registre), 201);
        String membreChemin = "/v1/admin/membres/" + code;
        String registreChemin = "/v1/admin/registres/" + code;
        List<String> piege = List.of("https://piege.exemple/retour");
        String detour = "https://piege.exemple:8443";

        // L'instructeur seul ne déplace ni le retour du citoyen, ni le connecteur.
        assertRefus(
                appeler(
                        instructrice(),
                        HttpMethod.PUT,
                        membreChemin,
                        avec(membre, "retours_autorises", piege)),
                409,
                "CONFLIT");
        assertRefus(
                appeler(
                        instructrice(),
                        HttpMethod.PUT,
                        registreChemin,
                        avec(registre, "adresse", detour)),
                409,
                "CONFLIT");
        Map<String, Object> retours = Map.of("retours_autorises", piege);
        Map<String, Object> adresse = Map.of("adresse", detour);
        assertRefus(
                appeler(instructrice(), HttpMethod.PUT, membreChemin + "/retours", retours),
                403,
                null);
        assertRefus(
                appeler(instructrice(), HttpMethod.PUT, registreChemin + "/adresse", adresse),
                403,
                null);
        assertThat(
                        reussi(lire(auditeur(), membreChemin), 200)
                                .path("retours_autorises")
                                .get(0)
                                .asString())
                .isEqualTo("https://guichet.exemple.bf/retour");
        reussi(
                appeler(instructrice(), HttpMethod.PUT, membreChemin, avec(membre, "nom", "G")),
                200);

        // L'autorité, elle, décide ; une adresse mal formée reste refusée.
        assertRefus(
                appeler(
                        autorite(),
                        HttpMethod.PUT,
                        membreChemin + "/retours",
                        Map.of("retours_autorises", List.of("javascript:alert(1)"))),
                400,
                "VALIDATION");
        assertRefus(
                appeler(
                        autorite(),
                        HttpMethod.PUT,
                        registreChemin + "/adresse",
                        Map.of("adresse", "http://guichet.exemple.bf")),
                400,
                "VALIDATION");
        assertThat(
                        reussi(
                                        appeler(
                                                autorite(),
                                                HttpMethod.PUT,
                                                membreChemin + "/retours",
                                                retours),
                                        200)
                                .path("retours_autorises")
                                .get(0)
                                .asString())
                .isEqualTo(piege.getFirst());
        assertThat(
                        reussi(
                                        appeler(
                                                autorite(),
                                                HttpMethod.PUT,
                                                registreChemin + "/adresse",
                                                adresse),
                                        200)
                                .path("adresse")
                                .asString())
                .isEqualTo(detour);
        assertRefus(
                appeler(autorite(), HttpMethod.PUT, "/v1/admin/registres/absent/adresse", adresse),
                404,
                null);
        var journal = lire(auditeur(), "/v1/admin/journal?type=ADMINISTRATION&taille=100");
        assertThat(journal.getBody()).contains("MODIFICATION_RETOURS", "MODIFICATION_ADRESSE");
    }

    @Test
    void leReferentielAmorceEstLisible() {
        JsonNode concours = reussi(lire(auditeur(), "/v1/admin/membres/concours"), 200);
        assertThat(concours.path("identite").asString()).isEqualTo(CONCOURS.uri());
        assertThat(concours.path("roles").get(0).asString()).isEqualTo("SERVICE");
        assertThat(concours.path("retours_autorises").get(0).asString()).isEqualTo(RETOUR_CONCOURS);
        JsonNode registres = reussi(lire(auditeur(), "/v1/admin/registres?taille=100"), 200);
        assertThat(valeurs(registres.path("elements"), "code"))
                .contains("etat-civil", "nationalite", "casier", "diplomes");
        assertThat(
                        reussi(lire(auditeur(), "/v1/admin/demarches/bourse-etudes"), 200)
                                .path("duree_autorisation_jours")
                                .asInt())
                .isZero();
        JsonNode habilitations =
                reussi(lire(auditeur(), "/v1/admin/habilitations?etat=APPROUVEE&taille=100"), 200);
        assertThat(valeurs(habilitations.path("elements"), "service"))
                .contains("concours", "bourses");
    }

    @Test
    void unJeuSEssaieSurUnePersonneFictiveSeulement() throws Exception {
        Map<String, Object> essai =
                Map.of("jeu", jeuDEssai(), "identifiant_unique", PERSONNE_FICTIVE);

        assertRefus(envoyer(auditeur(), "/v1/admin/jeux/essai", essai), 403, null);
        assertRefus(
                envoyer(
                        instructrice(),
                        "/v1/admin/jeux/essai",
                        avec(essai, "identifiant_unique", nouvellePersonne())),
                400,
                "VALIDATION");

        JsonNode resultat = reussi(envoyer(instructrice(), "/v1/admin/jeux/essai", essai), 200);
        assertThat(resultat.path("resultat").path("valeur").asBoolean()).isTrue();
        // La passerelle se désigne comme demandeur : la réponse n'est chiffrée pour aucun service.
        assertThat(ETAT_CIVIL.recues(PERSONNE_FICTIVE).getLast().getJSONObjectClaim("demandeur"))
                .containsEntry("membre", IdentiteMembre.PASSERELLE.uri());
        // Rien n'est publié : le jeu essayé n'existe pas dans le référentiel.
        assertRefus(lire(auditeur(), "/v1/admin/jeux/etat-civil.vivant"), 404, null);
    }

    @Test
    void lesStatistiquesComptentLaJournee() {
        JsonNode avant = reussi(lire(auditeur(), "/v1/admin/statistiques"), 200);
        String personne = nouvellePersonne();
        String pseudonyme = suivre(CONCOURS, accordee(personne)).path("pseudonyme").asString();
        // Désigné par son pseudonyme depuis un poste d'agent : accepté d'office.
        creer(
                CONCOURS,
                avec(
                        demande("concours-direct", "NOTIFICATION", CHIFFREMENT_CONCOURS),
                        "pseudonyme",
                        pseudonyme));

        JsonNode apres = reussi(lire(auditeur(), "/v1/admin/statistiques"), 200);
        assertThat(apres.path("demandes_jour").asInt())
                .isGreaterThanOrEqualTo(avant.path("demandes_jour").asInt() + 2);
        assertThat(apres.path("accords_jour").asInt())
                .isGreaterThanOrEqualTo(avant.path("accords_jour").asInt() + 2);
        assertThat(apres.path("consultations_jour").asInt())
                .isGreaterThanOrEqualTo(avant.path("consultations_jour").asInt() + 3);
        assertThat(apres.path("pieces_evitees_total").asInt())
                .isGreaterThanOrEqualTo(avant.path("pieces_evitees_total").asInt() + 3);
        assertRefus(
                lire(client(PKI.bundle(PKI.tls(CONCOURS)), null), "/v1/admin/statistiques"),
                401,
                null);
    }
}
