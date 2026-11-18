package bf.sira.connecteur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.TypesJose;
import bf.sira.commun.test.PkiDeTest;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Chaque règle de sécurité du connecteur, prouvée par son refus. */
@ExtendWith(OutputCaptureExtension.class)
class RefusTest extends ConnecteurDeTest {

    private static final String PROBLEME = "application/problem+json";

    private void assertRefuseSansAppelerLeRegistre(
            int statut, String erreur, ResponseEntity<String> reponse, int appelsAvant) {
        assertProbleme(statut, erreur, reponse);
        assertThat(REGISTRE_EXISTANT.appels()).isEqualTo(appelsAvant);
    }

    private static void assertProbleme(int statut, String erreur, ResponseEntity<String> reponse) {
        assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
        assertThat(reponse.getHeaders().getContentType().toString()).startsWith(PROBLEME);
        assertThat(reponse.getBody())
                .contains("urn:sira:erreur:" + erreur)
                .doesNotContain("attestation")
                .doesNotContain(NOM)
                .doesNotContain(NOM_DU_PERE)
                .doesNotContain(NAISSANCE);
    }

    // --- Qui appelle : seule la passerelle, reconnue à son certificat -----------------------

    @Test
    void unAutreMembreQueLaPasserelleEstRefuse() {
        int appels = REGISTRE_EXISTANT.appels();
        String jws = signer(requete(ADULTE, majeur()));

        for (IdentiteMembre intrus :
                new IdentiteMembre[] {
                    SERVICE,
                    IdentiteMembre.registre("casier"),
                    REGISTRE,
                    IdentiteMembre.CONSOLE,
                    IdentiteMembre.ESPACE_CITOYEN
                }) {
            assertRefuseSansAppelerLeRegistre(403, "acces-refuse", envoyer(intrus, jws), appels);
        }
    }

    @Test
    void sansCertificatOuAvecCeluiDUneAutreAutoriteLaConnexionEstRefusee() {
        String jws = signer(requete(ADULTE, majeur()));
        RestClient anonyme = client(PKI.bundleSansCertificat());
        RestClient etranger =
                client(
                        PkiDeTest.bundle(
                                PkiDeTest.creer().tls(IdentiteMembre.PASSERELLE), PKI.autorite()));

        for (RestClient client : new RestClient[] {anonyme, etranger}) {
            assertThatThrownBy(() ->
                            client.post()
                                    .uri("/v1/requetes")
                                    .contentType(MediaType.parseMediaType("application/jose"))
                                    .body(jws)
                                    .retrieve()
                                    .toEntity(String.class))
                    .isInstanceOf(ResourceAccessException.class);
        }
    }

    @Test
    void aucuneAutreRouteNEstOuverte() {
        RestClient passerelle = client(PKI.bundle(PKI.tls(IdentiteMembre.PASSERELLE)));

        for (String chemin :
                new String[] {"/v1/requetes", "/v1/journal", "/actuator/health", "/"}) {
            assertThat(
                            passerelle
                                    .get()
                                    .uri(chemin)
                                    .retrieve()
                                    .toEntity(String.class)
                                    .getStatusCode()
                                    .value())
                    .isEqualTo(403);
        }
    }

    // --- Ce qui est présenté : une requête signée par la passerelle, pour ce registre -------

    @Test
    void uneRequeteQuiNEstPasSigneeParLaPasserelleEstRefusee() {
        int appels = REGISTRE_EXISTANT.appels();
        JWTClaimsSet.Builder duService = requete(ADULTE, majeur()).issuer(SERVICE.uri());
        String signeeParLeService =
                new Signataire(PKI.cachet(SERVICE)).signer(TypesJose.REQUETE, duService.build());
        String signeeParUneAutreAutorite =
                new Signataire(PkiDeTest.creer().cachet(IdentiteMembre.PASSERELLE))
                        .signer(TypesJose.REQUETE, requete(ADULTE, majeur()).build());
        String dUnAutreType =
                PASSERELLE.signer(TypesJose.ATTESTATION, requete(ADULTE, majeur()).build());

        for (String jws :
                new String[] {
                    signeeParLeService, signeeParUneAutreAutorite, dUnAutreType, "pas.un.jws", "x"
                }) {
            assertRefuseSansAppelerLeRegistre(
                    403, "acces-refuse", envoyer(IdentiteMembre.PASSERELLE, jws), appels);
        }
    }

    @Test
    void uneRequeteModifieeApresSignatureEstRefusee() {
        int appels = REGISTRE_EXISTANT.appels();
        String[] parties = signer(requete(MAJEUR_DEMAIN, majeur())).split("\\.");
        String contenu =
                new String(Base64.getUrlDecoder().decode(parties[1]), StandardCharsets.UTF_8)
                        .replace(MAJEUR_DEMAIN, ADULTE);
        String modifiee =
                parties[0]
                        + "."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(contenu.getBytes(StandardCharsets.UTF_8))
                        + "."
                        + parties[2];

        assertRefuseSansAppelerLeRegistre(
                403, "acces-refuse", envoyer(IdentiteMembre.PASSERELLE, modifiee), appels);
    }

    @Test
    void uneRequeteDestineeAUnAutreRegistreEstRefusee() {
        int appels = REGISTRE_EXISTANT.appels();

        assertRefuseSansAppelerLeRegistre(
                403,
                "acces-refuse",
                envoyer(requete(ADULTE, majeur()).audience("urn:sira:registre:casier")),
                appels);
    }

    @Test
    void uneRequeteExpireeOuValableTropLongtempsEstRefusee() {
        int appels = REGISTRE_EXISTANT.appels();
        Instant maintenant = Instant.now();
        JWTClaimsSet.Builder expiree =
                requete(ADULTE, majeur())
                        .issueTime(Date.from(maintenant.minusSeconds(120)))
                        .expirationTime(Date.from(maintenant.minusSeconds(60)));
        JWTClaimsSet.Builder tropLongue =
                requete(ADULTE, majeur()).expirationTime(Date.from(maintenant.plusSeconds(300)));
        JWTClaimsSet.Builder sansEcheance = requete(ADULTE, majeur()).expirationTime(null);

        assertRefuseSansAppelerLeRegistre(403, "acces-refuse", envoyer(expiree), appels);
        assertRefuseSansAppelerLeRegistre(403, "acces-refuse", envoyer(tropLongue), appels);
        assertRefuseSansAppelerLeRegistre(403, "acces-refuse", envoyer(sansEcheance), appels);
    }

    @Test
    void uneRequeteRejoueeEstRefusee() {
        String jws = signer(requete(ADULTE, majeur()));

        assertThat(envoyer(IdentiteMembre.PASSERELLE, jws).getStatusCode().value()).isEqualTo(200);
        int appels = REGISTRE_EXISTANT.appels();
        assertRefuseSansAppelerLeRegistre(
                403, "acces-refuse", envoyer(IdentiteMembre.PASSERELLE, jws), appels);
    }

    @Test
    void unCorpsQuiNEstPasUnJwsEstRefuseDesLEntree() {
        ResponseEntity<String> reponse =
                client(PKI.bundle(PKI.tls(IdentiteMembre.PASSERELLE)))
                        .post()
                        .uri("/v1/requetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"jeu\": {}}")
                        .retrieve()
                        .toEntity(String.class);
        ResponseEntity<String> enorme = envoyer(IdentiteMembre.PASSERELLE, "a".repeat(300 * 1024));

        assertProbleme(415, "format-non-pris-en-charge", reponse);
        assertProbleme(413, "corps-trop-volumineux", enorme);
    }

    // --- Ce qui est demandé : une requête bien formée ---------------------------------------

    @Test
    void uneRequeteMalFormeeEstRefuseeSansAppelerLeRegistre() {
        int appels = REGISTRE_EXISTANT.appels();
        Map<String, Object> sujet = Map.of("pseudonyme", PSEUDONYME);

        JWTClaimsSet.Builder[] invalides = {
            // L'identifiant rejoint l'adresse appelée chez le registre : aucun chemin possible.
            requete("../../admin", majeur()),
            requete("1/documents/extrait-naissance", majeur()),
            requete(ADULTE, majeur()).claim("sujet", sujet),
            requete(ADULTE, majeur()).claim("jeu", null),
            requete(ADULTE, majeur()).claim("finalite", " "),
            requete(ADULTE, majeur()).claim("autorisation", null),
            requete(ADULTE, statut("date_naissance", "CONTIENT", "1990")),
            requete(ADULTE, avec(majeur(), "niveau", "TOUT")),
            requete(ADULTE, avec(majeur(), "validite", "toujours")),
            requete(ADULTE, document("../../etc/passwd")),
            // Le demandeur est un service : un registre ne reçoit pas d'attestation.
            requete(ADULTE, majeur())
                    .claim(
                            "demandeur",
                            demandeur(IdentiteMembre.registre("casier"), CHIFFREMENT_DU_SERVICE))
        };

        for (JWTClaimsSet.Builder invalide : invalides) {
            assertRefuseSansAppelerLeRegistre(400, "requete-invalide", envoyer(invalide), appels);
            assertThat(issueAuJournal(invalide)).isNull();
        }
    }

    @Test
    void unJeuIncoherentEstRefuse() {
        int appels = REGISTRE_EXISTANT.appels();

        JWTClaimsSet.Builder[] incoherents = {
            requete(ADULTE, Map.of("code", "etat-civil.question", "niveau", "STATUT")),
            requete(ADULTE, avec(majeur(), "champs", new String[] {"nom"})),
            requete(ADULTE, avec(champs("nom"), "document", "extrait-naissance")),
            requete(ADULTE, statut("date_naissance", "AGE_AU_MOINS", "dix-huit")),
            requete(ADULTE, statut("date_naissance", "AGE_AU_MOINS", null)),
            requete(ADULTE, statut("date_deces", "EXISTE", true)),
            requete(ADULTE, statut("nom' OR '1'='1", "EXISTE", null)),
            requete(ADULTE, statut("getClass().forName('java.lang.Runtime')", "EXISTE", null)),
            requete(ADULTE, champs("nom", "${jndi:ldap://x}")),
            requete(ADULTE, avec(majeur(), "validite", "PT0S"))
        };

        for (JWTClaimsSet.Builder incoherent : incoherents) {
            assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(incoherent), appels);
            assertThat(issueAuJournal(incoherent)).isEqualTo("JEU_INVALIDE");
        }
    }

    // --- Ce que le registre accepte : sa politique prime sur la passerelle ------------------

    @Test
    void laPolitiqueLocalePrimeSurCeQueDemandeLaPasserelle() {
        int appels = REGISTRE_EXISTANT.appels();
        JWTClaimsSet.Builder champAbsent = requete(ADULTE, statut("nom_pere", "EXISTE", null));
        JWTClaimsSet.Builder champAbsentEnClair = requete(ADULTE, champs("nom", "nom_pere"));
        JWTClaimsSet.Builder niveauDepasse = requete(ADULTE, champs("date_naissance"));
        JWTClaimsSet.Builder operateurInterdit =
                requete(ADULTE, statut("date_naissance", "EGAL", NAISSANCE));
        JWTClaimsSet.Builder seuilInterdit =
                requete(ADULTE, statut("date_naissance", "AGE_AU_MOINS", 36));
        JWTClaimsSet.Builder documentInterdit = requete(ADULTE, document("acte-mariage"));

        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(champAbsent), appels);
        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(champAbsentEnClair), appels);
        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(niveauDepasse), appels);
        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(operateurInterdit), appels);
        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(seuilInterdit), appels);
        assertRefuseSansAppelerLeRegistre(422, "jeu-refuse", envoyer(documentInterdit), appels);
        assertThat(issueAuJournal(champAbsent)).isEqualTo("CHAMP_NON_EXPOSE");
        assertThat(issueAuJournal(champAbsentEnClair)).isEqualTo("CHAMP_NON_EXPOSE");
        assertThat(issueAuJournal(niveauDepasse)).isEqualTo("NIVEAU_DEPASSE");
        assertThat(issueAuJournal(operateurInterdit)).isEqualTo("QUESTION_NON_AUTORISEE");
        assertThat(issueAuJournal(seuilInterdit)).isEqualTo("QUESTION_NON_AUTORISEE");
        assertThat(issueAuJournal(documentInterdit)).isEqualTo("DOCUMENT_NON_AUTORISE");
    }

    // --- Pour qui l'on chiffre : le service nommé dans la requête signée --------------------

    @Test
    void onNeChiffreQuePourLeCertificatDeChiffrementDuServiceNomme() {
        int appels = REGISTRE_EXISTANT.appels();
        IdentiteMembre bourses = IdentiteMembre.service("bourses");
        JWTClaimsSet.Builder dUnAutreService =
                requete(ADULTE, champs("nom"))
                        .claim("demandeur", demandeur(SERVICE, PKI.chiffrement(bourses)));
        JWTClaimsSet.Builder dUneAutreAutorite =
                requete(ADULTE, champs("nom"))
                        .claim(
                                "demandeur",
                                demandeur(SERVICE, PkiDeTest.creer().chiffrement(SERVICE)));
        JWTClaimsSet.Builder deTransport =
                requete(ADULTE, champs("nom"))
                        .claim("demandeur", demandeur(SERVICE, PKI.tls(SERVICE)));
        JWTClaimsSet.Builder illisible =
                requete(ADULTE, champs("nom"))
                        .claim(
                                "demandeur",
                                Map.of(
                                        "membre",
                                        SERVICE.uri(),
                                        "certificat_chiffrement",
                                        "pas un certificat"));

        for (JWTClaimsSet.Builder requete :
                new JWTClaimsSet.Builder[] {
                    dUnAutreService, dUneAutreAutorite, deTransport, illisible
                }) {
            // Le certificat est refusé avant que le registre ne soit consulté.
            assertRefuseSansAppelerLeRegistre(403, "acces-refuse", envoyer(requete), appels);
            assertThat(issueAuJournal(requete)).isEqualTo("CERTIFICAT_REFUSE");
        }
    }

    // --- Ce que répond le registre existant -------------------------------------------------

    @Test
    void unePersonneInconnueRepond404() {
        JWTClaimsSet.Builder requete = requete(INCONNU, majeur());
        JWTClaimsSet.Builder documentAbsent = requete(INCONNU, document("extrait-naissance"));

        assertProbleme(404, "personne-inconnue", envoyer(requete));
        assertProbleme(404, "personne-inconnue", envoyer(documentAbsent));
        assertThat(issueAuJournal(requete)).isEqualTo("PERSONNE_INCONNUE");
    }

    @Test
    void unRegistreEnPanneLentOuIncoherentRepond503() {
        for (String identifiant : new String[] {EN_PANNE, LENT, ILLISIBLE, PAGE_HTML}) {
            JWTClaimsSet.Builder requete = requete(identifiant, champs("nom"));

            assertProbleme(503, "registre-indisponible", envoyer(requete));
            assertThat(issueAuJournal(requete)).isEqualTo("REGISTRE_INDISPONIBLE");
        }
    }

    @Test
    void uneFicheQueLaRegleNeSaitPasLireEstRefuseeSansCiterLaValeur() {
        // « sexe » vaut « F » : ce n'est pas une date, la règle ne s'y applique pas.
        JWTClaimsSet.Builder requete = requete(ADULTE, statut("sexe", "ENCORE_VALIDE", null));

        assertProbleme(422, "jeu-refuse", envoyer(requete));
        assertThat(issueAuJournal(requete)).isEqualTo("REGLE_INAPPLICABLE");
    }

    // --- Ce qui ne doit jamais sortir -------------------------------------------------------

    @Test
    void aucuneValeurDuRegistreNiIdentifiantDansLesJournauxApplicatifs(CapturedOutput sortie) {
        envoyer(requete(ADULTE, majeur()));
        envoyer(requete(ADULTE, champs("nom", "prenoms")));
        envoyer(requete(ADULTE, statut("date_naissance", "EGAL", NAISSANCE)));
        envoyer(requete(ADULTE, statut("sexe", "ENCORE_VALIDE", null)));
        envoyer(
                requete(ADULTE, champs("nom"))
                        .claim("demandeur", demandeur(SERVICE, PKI.tls(SERVICE))));
        envoyer(requete(ILLISIBLE, champs("nom")));
        envoyer(requete(PAGE_HTML, champs("nom")));
        envoyer(requete(INCONNU, majeur()));

        assertThat(sortie.getAll())
                .doesNotContain(NOM)
                .doesNotContain(NOM_DU_PERE)
                .doesNotContain("Awa Mariam")
                .doesNotContain(NAISSANCE)
                .doesNotContain(ADULTE)
                .doesNotContain(ILLISIBLE)
                .doesNotContain(INCONNU)
                .doesNotContain(PSEUDONYME);
    }
}
