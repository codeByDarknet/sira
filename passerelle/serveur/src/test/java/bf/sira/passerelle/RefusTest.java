package bf.sira.passerelle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import bf.sira.passerelle.collecte.Purge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Chaque règle de sécurité de la passerelle, prouvée par le refus qu'elle oppose. */
class RefusTest extends PasserelleDeTest {

    @Autowired Purge purge;

    // --- Qui appelle ------------------------------------------------------------------------

    @Test
    void sansCertificatOuAvecUnCertificatEtrangerLaConnexionEstRefusee() {
        assertThatThrownBy(() -> lire(client(PKI.bundleSansCertificat(), null), "/v1/demandes/x"))
                .isInstanceOf(ResourceAccessException.class);
        PkiDeTest etrangere = PkiDeTest.creer();
        assertThatThrownBy(() ->
                        lire(
                                client(
                                        PkiDeTest.bundle(etrangere.tls(CONCOURS), PKI.autorite()),
                                        null),
                                "/v1/demandes/x"))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void lesRoutesDesServicesNAcceptentQueDesServices() {
        Map<String, Object> corps = demande("concours-direct", "CODE", CHIFFREMENT_CONCOURS);
        String cle = UUID.randomUUID().toString();

        assertRefus(creer(IdentiteMembre.registre("etat-civil"), cle, corps), 403, null);
        assertRefus(creer(IdentiteMembre.ESPACE_CITOYEN, cle, corps), 403, null);
        assertRefus(creer(IdentiteMembre.CONSOLE, cle, corps), 403, null);
        // Un service que l'autorité a certifié mais que le référentiel ne connaît pas.
        assertRefus(creer(IdentiteMembre.service("inconnu"), cle, corps), 403, null);
        // Hors des routes publiées, rien n'existe pour personne.
        assertRefus(lire(membre(CONCOURS), "/v1/journal"), 403, null);
    }

    @Test
    void lesDeuxRoyaumesNeSeCroisentPas() {
        String personne = nouvellePersonne();
        var roles = Map.of("roles", java.util.List.of("instructeur", "autorite", "auditeur"));
        // Chaque jeton est lié au certificat qui le présente : seul le royaume ou le membre cloche.
        String citoyenParLaConsole =
                ROYAUME_CITOYENS.jetonAcces(certificatConsole(), r -> r.claim("iue", personne));
        String administrateurParLEspace =
                ROYAUME_SOCLE.jetonAcces(
                        certificatEspaceCitoyen(),
                        r -> r.claim("iue", personne).claim("realm_access", roles));
        var tlsDuService = PKI.tls(CONCOURS);
        String citoyenParUnService =
                ROYAUME_CITOYENS.jetonAcces(
                        tlsDuService.certificat(), r -> r.claim("iue", personne));

        // Un jeton de l'autre royaume n'est pas reconnu, même présenté par le bon serveur.
        assertRefus(
                lire(client(espaceCitoyen(), administrateurParLEspace), "/v1/citoyen/demandes"),
                401,
                null);
        assertRefus(lire(client(laConsole(), citoyenParLaConsole), "/v1/admin/membres"), 401, null);
        // Un jeton du bon royaume ne vaut que présenté par le serveur d'application du préfixe.
        assertRefus(
                lire(client(laConsole(), citoyenParLaConsole), "/v1/citoyen/demandes"), 403, null);
        assertRefus(
                lire(client(espaceCitoyen(), administrateurParLEspace), "/v1/admin/membres"),
                403,
                null);
        assertRefus(
                lire(client(PKI.bundle(tlsDuService), citoyenParUnService), "/v1/citoyen/demandes"),
                403,
                null);
        // Sans jeton, le certificat seul ne suffit pas.
        assertRefus(lire(client(espaceCitoyen(), null), "/v1/citoyen/demandes"), 401, null);
        assertRefus(lire(client(laConsole(), null), "/v1/admin/membres"), 401, null);

        reussi(lire(citoyen(personne), "/v1/citoyen/demandes"), 200);
        reussi(lire(console("awa", "auditeur"), "/v1/admin/membres"), 200);
    }

    @Test
    void leJetonDoitEtreLieAuCertificatEtDesignerLeCitoyen() {
        String personne = nouvellePersonne();

        // Jeton émis pour un autre serveur d'application : volé, il ne sert à rien ici.
        String lieAilleurs =
                ROYAUME_CITOYENS.jetonAcces(certificatConsole(), r -> r.claim("iue", personne));
        assertRefus(lire(client(espaceCitoyen(), lieAilleurs), "/v1/citoyen/demandes"), 401, null);

        String autreAudience =
                ROYAUME_CITOYENS.jetonAcces(
                        certificatEspaceCitoyen(),
                        r -> r.claim("iue", personne).audience("autre-service"));
        assertRefus(
                lire(client(espaceCitoyen(), autreAudience), "/v1/citoyen/demandes"), 401, null);

        String sansIdentifiant = ROYAUME_CITOYENS.jetonAcces(certificatEspaceCitoyen(), r -> {});
        assertRefus(
                lire(client(espaceCitoyen(), sansIdentifiant), "/v1/citoyen/demandes"), 403, null);

        String identifiantForge =
                ROYAUME_CITOYENS.jetonAcces(
                        certificatEspaceCitoyen(), r -> r.claim("iue", "1' OR '1'='1"));
        assertRefus(
                lire(client(espaceCitoyen(), identifiantForge), "/v1/citoyen/demandes"), 403, null);
    }

    // --- Ce qu'un service peut demander -----------------------------------------------------

    @Test
    void unServiceNeDemandeQueCePourQuoiIlEstHabilite() {
        assertRefus(
                creer(
                        BOURSES,
                        UUID.randomUUID().toString(),
                        demande("concours-direct", "CODE", CHIFFREMENT_BOURSES)),
                403,
                "HABILITATION_ABSENTE");
        assertRefus(
                creer(
                        CONCOURS,
                        UUID.randomUUID().toString(),
                        demande("demarche-inconnue", "CODE", CHIFFREMENT_CONCOURS)),
                403,
                "HABILITATION_ABSENTE");
        assertRefus(
                lire(membre(BOURSES), "/v1/demarches/concours-direct"),
                403,
                "HABILITATION_ABSENTE");

        JsonNode decrite = reussi(lire(membre(CONCOURS), "/v1/demarches/concours-direct"), 200);
        assertThat(valeurs(decrite.path("jeux"), "code"))
                .containsExactlyInAnyOrder(
                        "etat-civil.majeur", "nationalite.burkinabe", "casier.neant");
    }

    @Test
    void laDemandeDUnAutreServiceNExistePas() {
        String personne = nouvellePersonne();
        String id = accordee(personne);

        assertRefus(lire(membre(BOURSES), "/v1/demandes/" + id), 404, "DEMANDE_INTROUVABLE");
        assertRefus(supprimer(membre(BOURSES), "/v1/demandes/" + id), 404, "DEMANDE_INTROUVABLE");
        assertRefus(
                lire(membre(CONCOURS), "/v1/demandes/" + UUID.randomUUID()),
                404,
                "DEMANDE_INTROUVABLE");
        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void uneSaisieInvalideEstRefuseeChampParChamp() {
        Map<String, Object> valide = demande("concours-direct", "CODE", CHIFFREMENT_CONCOURS);
        String cle = UUID.randomUUID().toString();

        ResponseEntity<String> refus =
                creer(CONCOURS, cle, avec(valide, "demarche", "Concours Direct"));
        assertRefus(refus, 400, "VALIDATION");
        assertThat(json(refus).path("erreurs").get(0).path("champ").asString())
                .isEqualTo("demarche");
        assertThat(refus.getBody()).doesNotContain("Concours Direct");

        assertRefus(creer(CONCOURS, cle, avec(valide, "canal", "FAX")), 400, null);
        assertRefus(
                creer(CONCOURS, cle, avec(valide, "certificat_chiffrement", "")),
                400,
                "VALIDATION");
        assertRefus(
                creer(CONCOURS, cle, avec(valide, "pseudonyme", "pseudonyme-connu")),
                400,
                "VALIDATION");
        assertRefus(
                creer(CONCOURS, cle, avec(valide, "identifiant_unique", "99-00")),
                400,
                "VALIDATION");
        assertRefus(
                creer(
                        CONCOURS,
                        cle,
                        demande("concours-direct", "NOTIFICATION", CHIFFREMENT_CONCOURS)),
                400,
                "VALIDATION");
        assertRefus(lire(membre(CONCOURS), "/v1/demandes/pas-un-uuid"), 400, null);
        assertRefus(
                membre(CONCOURS)
                        .post()
                        .uri("/v1/demandes")
                        .header("Idempotency-Key", cle)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"demarche\": ")
                        .retrieve()
                        .toEntity(String.class),
                400,
                null);
    }

    @Test
    void laCleDIdempotenceEstExigeeEtNeSertQuUneFois() {
        Map<String, Object> corps = demande("concours-direct", "CODE", CHIFFREMENT_CONCOURS);

        assertRefus(envoyer(membre(CONCOURS), "/v1/demandes", corps), 400, null);
        assertRefus(creer(CONCOURS, "court", corps), 400, null);

        String cle = UUID.randomUUID().toString();
        JsonNode premiere = reussi(creer(CONCOURS, cle, corps), 201);
        JsonNode rejouee = reussi(creer(CONCOURS, cle, corps), 201);
        // Même demande ; ses codes ne sont rendus qu'une fois, la passerelle ne les garde pas.
        assertThat(rejouee.path("id")).isEqualTo(premiere.path("id"));
        assertThat(rejouee.path("expire_le")).isEqualTo(premiere.path("expire_le"));
        assertThat(premiere.path("code_court").asString()).isNotBlank();
        assertThat(rejouee.path("code_court").isNull()).isTrue();
        assertThat(rejouee.path("code_verification").isNull()).isTrue();
        // La base ne garde aucun des deux codes en clair, sous aucune forme tapable.
        String codeCourt = premiere.path("code_court").asString();
        for (String forme :
                List.of(
                        codeCourt,
                        codeCourt.replace("-", ""),
                        premiere.path("code_verification").asString())) {
            assertThat(
                            sql.sql(
                                            "SELECT count(*) FROM demande d WHERE d.id = ?::uuid"
                                                    + " AND d::text LIKE ?")
                                    .params(premiere.path("id").asString(), "%" + forme + "%")
                                    .query(Long.class)
                                    .single())
                    .isZero();
        }
        assertRefus(creer(CONCOURS, cle, avec(corps, "canal", "REDIRECTION")), 409, "CONFLIT");
        // La clé d'un service ne dit rien à un autre.
        JsonNode chezBourses =
                reussi(
                        creer(BOURSES, cle, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES)),
                        201);
        assertThat(chezBourses.path("id").asString()).isNotEqualTo(premiere.path("id").asString());
    }

    @Test
    void leRetourNeVaQueVersUneAdresseDeclaree() {
        Map<String, Object> corps = demande("concours-direct", "REDIRECTION", CHIFFREMENT_CONCOURS);

        for (String adresse :
                new String[] {
                    "https://pirate.exemple/retour",
                    RETOUR_CONCOURS + "/../autre",
                    RETOUR_CONCOURS + "?suite=https://pirate.exemple",
                    "https://concours.exemple.bf.pirate.exemple/retour"
                }) {
            ResponseEntity<String> refus =
                    creer(
                            CONCOURS,
                            UUID.randomUUID().toString(),
                            avec(corps, "retour_url", adresse));
            assertRefus(refus, 400, "VALIDATION");
            assertThat(json(refus).path("erreurs").get(0).path("champ").asString())
                    .isEqualTo("retour_url");
        }
        assertRefus(creer(CONCOURS, UUID.randomUUID().toString(), corps), 400, "VALIDATION");
    }

    @Test
    void lesAttestationsNeSontChiffreesQuePourLeServiceQuiDemande() {
        Map<String, Object> corps = demande("concours-direct", "CODE", CHIFFREMENT_CONCOURS);
        PkiDeTest etrangere = PkiDeTest.creer();

        for (var certificat :
                new java.security.cert.X509Certificate[] {
                    CHIFFREMENT_BOURSES.certificat(),
                    etrangere.chiffrement(CONCOURS).certificat(),
                    PKI.tls(CONCOURS).certificat(),
                    PKI.cachet(CONCOURS).certificat()
                }) {
            ResponseEntity<String> refus =
                    creer(
                            CONCOURS,
                            UUID.randomUUID().toString(),
                            avec(corps, "certificat_chiffrement", Certificats.versPem(certificat)));
            assertRefus(refus, 400, "VALIDATION");
            assertThat(json(refus).path("erreurs").get(0).path("champ").asString())
                    .isEqualTo("certificat_chiffrement");
        }
        assertRefus(
                creer(
                        CONCOURS,
                        UUID.randomUUID().toString(),
                        avec(corps, "certificat_chiffrement", "-----BEGIN CERTIFICATE-----\nAAAA")),
                400,
                "VALIDATION");
    }

    // --- Ce qu'un citoyen peut faire --------------------------------------------------------

    @Test
    void laDemandeDUnAutreCitoyenNExistePas() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        String id = notifier(personne).path("id").asString();
        String chemin = "/v1/citoyen/demandes/" + id;

        assertRefus(lire(citoyen(autre), chemin), 404, "DEMANDE_INTROUVABLE");
        assertRefus(
                envoyer(citoyen(autre), chemin + "/rattachement", null),
                404,
                "DEMANDE_INTROUVABLE");
        assertRefus(
                envoyer(citoyen(autre), chemin + "/decision", Map.of("decision", "ACCEPTER")),
                404,
                "DEMANDE_INTROUVABLE");
        assertThat(reussi(lire(citoyen(autre), "/v1/citoyen/demandes"), 200).path("total").asInt())
                .isZero();
        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
    }

    @Test
    void uneDemandeRattacheeNeChangePasDeMains() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        JsonNode creee = creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
        Map<String, String> code = Map.of("code_court", creee.path("code_court").asString());

        reussi(envoyer(citoyen(personne), "/v1/citoyen/demandes/rattachement", code), 200);

        assertRefus(
                envoyer(citoyen(autre), "/v1/citoyen/demandes/rattachement", code),
                404,
                "CODE_INVALIDE");
        assertRefus(
                envoyer(
                        citoyen(autre),
                        "/v1/citoyen/demandes/" + creee.path("id").asString() + "/rattachement",
                        null),
                404,
                "DEMANDE_INTROUVABLE");
    }

    @Test
    void uneDecisionNeSePrendQuUneFoisEtDansLeDelai() {
        String personne = nouvellePersonne();
        String id = notifier(personne).path("id").asString();
        String chemin = "/v1/citoyen/demandes/" + id + "/decision";

        assertRefus(envoyer(citoyen(personne), chemin, Map.of("decision", "PEUT-ETRE")), 400, null);
        assertRefus(envoyer(citoyen(personne), chemin, Map.of()), 400, "VALIDATION");
        decider(personne, id, "REFUSER");
        assertRefus(
                envoyer(citoyen(personne), chemin, Map.of("decision", "ACCEPTER")),
                409,
                "DECISION_DEJA_PRISE");
        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("REFUSEE");

        String tardive = notifier(personne).path("id").asString();
        sql.sql("UPDATE demande SET expire_le = now() - interval '1 second' WHERE id = ?::uuid")
                .param(tardive)
                .update();
        assertRefus(
                envoyer(
                        citoyen(personne),
                        "/v1/citoyen/demandes/" + tardive + "/decision",
                        Map.of("decision", "ACCEPTER")),
                410,
                "DEMANDE_EXPIREE");
        purge.purger();
        assertThat(suivre(CONCOURS, tardive).path("etat").asString()).isEqualTo("EXPIREE");
        assertThat(ETAT_CIVIL.recues(personne)).isEmpty();
    }

    @Test
    void lesEssaisDeCodeCourtSontComptes() {
        String personne = nouvellePersonne();
        JsonNode creee = creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
        String chemin = "/v1/citoyen/demandes/rattachement";

        assertRefus(
                envoyer(citoyen(personne), chemin, Map.of("code_court", "KLPM-48!1")), 400, null);
        for (int essai = 0; essai < 5; essai++) {
            assertRefus(
                    envoyer(citoyen(personne), chemin, Map.of("code_court", "ZZZZ-000" + essai)),
                    404,
                    "CODE_INVALIDE");
        }
        // Au-delà de la limite, même le bon code est refusé : deviner ne paie pas.
        assertRefus(
                envoyer(
                        citoyen(personne),
                        chemin,
                        Map.of("code_court", creee.path("code_court").asString())),
                429,
                "TROP_DE_REQUETES");
        // Un autre citoyen n'en pâtit pas.
        reussi(
                envoyer(
                        citoyen(nouvellePersonne()),
                        chemin,
                        Map.of("code_court", creee.path("code_court").asString())),
                200);
    }

    // --- Accès des personnes : nul n'obtient le dossier d'un autre ---------------------------

    @Test
    void uneNotificationParIdentifiantNEstJamaisAccepteeDOffice() {
        String personne = nouvellePersonne();
        accordee(personne);

        // N'importe qui peut taper l'identifiant de cette personne sur l'écran d'un service : même
        // avec son autorisation active, la demande attend qu'elle l'accepte elle-même.
        String id = notifier(personne).path("id").asString();
        JsonNode suivi = suivre(CONCOURS, id);
        assertThat(suivi.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(suivi.path("pseudonyme").isNull()).isTrue();
        assertThat(suivi.path("elements").size()).isZero();
        assertThat(ETAT_CIVIL.recues(personne)).hasSize(1);
    }

    @Test
    void unIdentifiantTapeNeCreeAucunCitoyen() {
        String inconnu = nouvellePersonne();
        String connu = nouvellePersonne();
        reussi(lire(citoyen(connu), "/v1/citoyen/resume"), 200);

        JsonNode pourInconnu = notifier(inconnu);
        JsonNode pourConnu = notifier(connu);
        // Rien ne dit au service si l'identifiant désigne quelqu'un.
        assertThat(pourInconnu.path("etat").asString())
                .isEqualTo(pourConnu.path("etat").asString())
                .isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(pourInconnu.propertyNames()).isEqualTo(pourConnu.propertyNames());
        assertThat(citoyensDe(inconnu)).isZero();

        // La demande lui est remise quand il se connecte, et à lui seul.
        assertThat(
                        reussi(lire(citoyen(inconnu), "/v1/citoyen/demandes"), 200)
                                .path("total")
                                .asInt())
                .isEqualTo(1);
        assertThat(citoyensDe(inconnu)).isOne();
    }

    // --- Désignation sur REDIRECTION et CODE : seule la personne saisie donne l'accord ---------

    /** Une demande par lien de retour ou par code court qui désigne cette personne. */
    JsonNode designer(String canal, String identifiantUnique) {
        Map<String, Object> corps =
                "REDIRECTION".equals(canal)
                        ? avec(
                                demande("concours-direct", "REDIRECTION", CHIFFREMENT_CONCOURS),
                                "retour_url",
                                RETOUR_CONCOURS)
                        : demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES);
        return creer(
                "REDIRECTION".equals(canal) ? CONCOURS : BOURSES,
                avec(corps, "identifiant_unique", identifiantUnique));
    }

    @Test
    void uneDemandeDesigneeParLienNExistePasPourUnAutre() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        JsonNode creee = designer("REDIRECTION", personne);
        String id = creee.path("id").asString();
        String chemin = "/v1/citoyen/demandes/" + id;
        // Les autres champs du canal ne changent pas.
        assertThat(creee.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(creee.path("code_court").isNull()).isTrue();
        assertThat(creee.path("code_verification").isNull()).isTrue();

        // L'autre reçoit la réponse d'une demande qui n'existe pas, même par le lien d'accord.
        String inexistante = "/v1/citoyen/demandes/" + UUID.randomUUID();
        for (String suffixe : List.of("", "/rattachement")) {
            var refus =
                    suffixe.isEmpty()
                            ? lire(citoyen(autre), chemin)
                            : envoyer(citoyen(autre), chemin + suffixe, null);
            var reference =
                    suffixe.isEmpty()
                            ? lire(citoyen(autre), inexistante)
                            : envoyer(citoyen(autre), inexistante + suffixe, null);
            assertRefus(refus, 404, "DEMANDE_INTROUVABLE");
            assertRefus(reference, 404, "DEMANDE_INTROUVABLE");
            for (String champ : List.of("title", "detail", "type")) {
                assertThat(json(refus).path(champ)).isEqualTo(json(reference).path(champ));
            }
        }
        for (String choix : List.of("ACCEPTER", "REFUSER")) {
            assertRefus(
                    envoyer(citoyen(autre), chemin + "/decision", Map.of("decision", choix)),
                    404,
                    "DEMANDE_INTROUVABLE");
        }
        assertThat(reussi(lire(citoyen(autre), "/v1/citoyen/demandes"), 200).path("total").asInt())
                .isZero();
        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");

        // La personne désignée ouvre le lien et accepte, sans code à taper : rien d'autre ne
        // change sur ce canal.
        reussi(envoyer(citoyen(personne), chemin + "/rattachement", null), 200);
        String retour = decider(personne, id, "ACCEPTER").path("retour_url").asString();
        assertThat(retour).startsWith(RETOUR_CONCOURS + "?");
        assertThat(ETAT_CIVIL.recues(personne)).isNotEmpty();
        assertThat(ETAT_CIVIL.recues(autre)).isEmpty();
    }

    @Test
    void uneDemandeDesigneeParCodeCourtNExistePasPourUnAutre() {
        String personne = nouvellePersonne();
        String autre = nouvellePersonne();
        JsonNode creee = designer("CODE", personne);
        String id = creee.path("id").asString();
        Map<String, String> code = Map.of("code_court", creee.path("code_court").asString());
        assertThat(creee.path("code_court").asString()).matches("[A-Z]{4}-[0-9]{4}");
        assertThat(creee.path("code_verification").asString()).matches("[0-9]{6}");

        // Pour l'autre, c'est un code erroné, compté comme tel.
        assertRefus(
                envoyer(citoyen(autre), "/v1/citoyen/demandes/rattachement", code),
                404,
                "CODE_INVALIDE");
        assertRefus(
                envoyer(citoyen(autre), "/v1/citoyen/demandes/" + id + "/rattachement", null),
                404,
                "DEMANDE_INTROUVABLE");
        assertRefus(
                envoyer(
                        citoyen(autre),
                        "/v1/citoyen/demandes/" + id + "/decision",
                        Map.of("decision", "REFUSER")),
                404,
                "DEMANDE_INTROUVABLE");
        assertThat(
                        sql.sql(
                                        "SELECT count(*) FROM essai_rattachement e JOIN citoyen c ON"
                                                + " c.id = e.citoyen WHERE c.identifiant_unique = ?")
                                .param(autre)
                                .query(Long.class)
                                .single())
                .isOne();

        // La personne désignée tape le code court, puis le code de vérification, et accepte.
        assertThat(
                        reussi(
                                        envoyer(
                                                citoyen(personne),
                                                "/v1/citoyen/demandes/rattachement",
                                                code),
                                        200)
                                .path("id")
                                .asString())
                .isEqualTo(id);
        assertThat(decider(personne, id, "ACCEPTER").path("etat").asString())
                .isIn("EN_COURS", "PARTIELLE", "COMPLETE");
    }

    @Test
    void unIdentifiantDesigneNeRevelePasSiLaPersonneExiste() {
        for (String canal : List.of("REDIRECTION", "CODE")) {
            String inconnu = nouvellePersonne();
            String connu = nouvellePersonne();
            reussi(lire(citoyen(connu), "/v1/citoyen/resume"), 200);

            JsonNode pourInconnu = designer(canal, inconnu);
            JsonNode pourConnu = designer(canal, connu);
            JsonNode sansDesignation =
                    "REDIRECTION".equals(canal)
                            ? creer(
                                    CONCOURS,
                                    avec(
                                            demande(
                                                    "concours-direct",
                                                    "REDIRECTION",
                                                    CHIFFREMENT_CONCOURS),
                                            "retour_url",
                                            RETOUR_CONCOURS))
                            : creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
            assertThat(pourInconnu.propertyNames())
                    .isEqualTo(pourConnu.propertyNames())
                    .isEqualTo(sansDesignation.propertyNames());
            for (String champ : List.of("etat", "code_court", "code_verification")) {
                assertThat(pourInconnu.path(champ).getNodeType())
                        .isEqualTo(pourConnu.path(champ).getNodeType())
                        .isEqualTo(sansDesignation.path(champ).getNodeType());
            }
            assertThat(pourInconnu.path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
            assertThat(citoyensDe(inconnu)).isZero();

            // Ni le journal ni la demande ne gardent l'identifiant en clair.
            String id = pourConnu.path("id").asString();
            assertThat(
                            sql.sql(
                                            "SELECT count(*) FROM demande d WHERE d.id = ?::uuid"
                                                    + " AND d::text LIKE ?")
                                    .params(id, "%" + connu + "%")
                                    .query(Long.class)
                                    .single())
                    .isZero();
            reussi(lire(citoyen(connu), "/v1/citoyen/demandes/" + id), 200);
            assertThat(
                            sql.sql("SELECT count(*) FROM journal j WHERE j::text LIKE ?")
                                    .param("%" + connu + "%")
                                    .query(Long.class)
                                    .single())
                    .isZero();
            assertThat(journalDe(connu).toString()).doesNotContain(connu);
        }
    }

    private long citoyensDe(String identifiantUnique) {
        return sql.sql("SELECT count(*) FROM citoyen WHERE identifiant_unique = ?")
                .param(identifiantUnique)
                .query(Long.class)
                .single();
    }

    @Test
    void accepterExigeLeCodeAfficheParLeService() {
        String personne = nouvellePersonne();
        String id = notifier(personne).path("id").asString();
        String bon = codeAffiche(id);
        String faux = bon.equals("000000") ? "111111" : "000000";

        assertRefus(decision(personne, id, "ACCEPTER", null), 422, "CODE_INVALIDE");
        assertRefus(decision(personne, id, "ACCEPTER", "12ab"), 400, "VALIDATION");
        for (int essai = 1; essai < 5; essai++) {
            assertRefus(decision(personne, id, "ACCEPTER", faux), 422, "CODE_INVALIDE");
        }
        // Cinq codes erronés : même le bon n'ouvre plus rien. Refuser reste possible.
        assertRefus(decision(personne, id, "ACCEPTER", bon), 429, "TROP_DE_REQUETES");
        assertThat(suivre(CONCOURS, id).path("etat").asString()).isEqualTo("EN_ATTENTE_ACCORD");
        assertThat(ETAT_CIVIL.recues(personne)).isEmpty();
        reussi(decision(personne, id, "REFUSER", null), 200);

        // Canal CODE : le code court rattache, le code de vérification seul fait accepter.
        JsonNode creee = creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
        String parCode = creee.path("id").asString();
        reussi(
                envoyer(
                        citoyen(personne),
                        "/v1/citoyen/demandes/rattachement",
                        Map.of("code_court", creee.path("code_court").asString())),
                200);
        assertRefus(decision(personne, parCode, "ACCEPTER", faux), 422, "CODE_INVALIDE");
        assertThat(
                        reussi(decision(personne, parCode, "ACCEPTER", codeAffiche(parCode)), 200)
                                .path("etat")
                                .asString())
                .isIn("EN_COURS", "PARTIELLE", "COMPLETE");
    }

    @Test
    void lesEssaisDeCodeCourtSimultanesNePassentPasEnsemble() throws Exception {
        RestClient client = citoyen(nouvellePersonne());
        List<Callable<Integer>> essais = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String code = "ZZZZ-%04d".formatted(i);
            essais.add(() ->
                    envoyer(client, "/v1/citoyen/demandes/rattachement", Map.of("code_court", code))
                            .getStatusCode()
                            .value());
        }
        List<Integer> statuts = new ArrayList<>();
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Integer> statut : executeur.invokeAll(essais)) {
                statuts.add(statut.get());
            }
        }
        assertThat(statuts).filteredOn(s -> s == 404).hasSize(5);
        assertThat(statuts).filteredOn(s -> s == 429).hasSize(15);
    }

    @Test
    void lesEssaisRepartisSurPlusieursComptesSontBornes() {
        String personne = nouvellePersonne();
        reussi(lire(citoyen(personne), "/v1/citoyen/resume"), 200);
        String interne =
                sql.sql("SELECT id FROM citoyen WHERE identifiant_unique = ?")
                        .param(personne)
                        .query(String.class)
                        .single();
        JsonNode creee = creer(BOURSES, demande("bourse-etudes", "CODE", CHIFFREMENT_BOURSES));
        // Mille codes erronés viennent d'être tapés, d'un compte ou de plusieurs.
        sql.sql(
                        "INSERT INTO essai_rattachement (citoyen, quand) SELECT ?, now() FROM"
                                + " generate_series(1, 1000)")
                .param(interne)
                .update();
        try {
            assertRefus(
                    envoyer(
                            citoyen(nouvellePersonne()),
                            "/v1/citoyen/demandes/rattachement",
                            Map.of("code_court", creee.path("code_court").asString())),
                    429,
                    "TROP_DE_REQUETES");
        } finally {
            sql.sql("DELETE FROM essai_rattachement WHERE citoyen = ?").param(interne).update();
        }
    }
}
