package bf.sira.connecteur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.pki.Bicle;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** De la requête signée de la passerelle à l'attestation lue par le service. */
class ParcoursTest extends ConnecteurDeTest {

    @Value("${local.management.port}")
    private int portDeGestion;

    private static Duration validite(JWTClaimsSet attestation) {
        return Duration.between(
                attestation.getIssueTime().toInstant(),
                attestation.getExpirationTime().toInstant());
    }

    @Test
    void laSondeDeSanteRepondSurLePortDeGestionSansRienDetailler() {
        ResponseEntity<String> sonde =
                RestClient.create("http://127.0.0.1:" + portDeGestion)
                        .get()
                        .uri("/actuator/health/readiness")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(sonde.getStatusCode().value()).isEqualTo(200);
        assertThat(sonde.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void uneQuestionRecoitUnOuiSigneEtChiffreSansLaValeur() throws Exception {
        JWTClaimsSet.Builder requete = requete(ADULTE, majeur());

        ResponseEntity<String> reponse = envoyer(requete);

        assertThat(reponse.getStatusCode().value()).isEqualTo(200);
        assertThat(reponse.getBody()).doesNotContain(NAISSANCE).doesNotContain(NOM);
        JWTClaimsSet attestation = ouvrir(reponse);
        assertThat(attestation.getIssuer()).isEqualTo(REGISTRE.uri());
        assertThat(attestation.getAudience()).containsExactly(SERVICE.uri());
        assertThat(attestation.getJWTID()).isEqualTo(requete.build().getJWTID());
        assertThat(attestation.getSubject()).isEqualTo(PSEUDONYME);
        assertThat(attestation.getClaim("demarche")).isEqualTo("concours-direct");
        assertThat(attestation.getClaim("jeu")).isEqualTo("etat-civil.question");
        assertThat(attestation.getClaim("niveau")).isEqualTo("STATUT");
        assertThat(attestation.getJSONObjectClaim("resultat")).isEqualTo(Map.of("valeur", true));
        // Ni la date de naissance ni l'identifiant unique ne sont dans ce que reçoit le service.
        assertThat(attestation.toString()).doesNotContain(NAISSANCE).doesNotContain(ADULTE);
        // La politique de l'état civil fixe 90 jours quand la requête ne propose rien.
        assertThat(validite(attestation)).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void lAgeSeCompteAuJourPres() {
        assertThat(ouvrir(envoyer(requete(MAJEURE_AUJOURDHUI, majeur()))).getClaim("resultat"))
                .isEqualTo(Map.of("valeur", true));
        assertThat(ouvrir(envoyer(requete(MAJEUR_DEMAIN, majeur()))).getClaim("resultat"))
                .isEqualTo(Map.of("valeur", false));
    }

    @Test
    void unChampAbsentRepondNonSansErreur() {
        JWTClaimsSet attestation =
                ouvrir(envoyer(requete(ADULTE, statut("date_deces", "EXISTE", null))));

        assertThat(attestation.getClaim("resultat")).isEqualTo(Map.of("valeur", false));
    }

    @Test
    void seulsLesChampsDemandesSortent() {
        JWTClaimsSet attestation = ouvrir(envoyer(requete(ADULTE, champs("nom", "prenoms"))));

        assertThat(attestation.getClaim("niveau")).isEqualTo("CHAMPS");
        assertThat(attestation.getClaim("resultat"))
                .isEqualTo(Map.of("champs", Map.of("nom", NOM, "prenoms", "Awa Mariam")));
    }

    @Test
    void unDocumentSortAvecSonEmpreinte() throws Exception {
        JWTClaimsSet attestation = ouvrir(envoyer(requete(ADULTE, document("extrait-naissance"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> document =
                (Map<String, Object>) attestation.getJSONObjectClaim("resultat").get("document");
        byte[] contenu = Base64.getDecoder().decode((String) document.get("contenu"));
        assertThat(contenu).isEqualTo(EXTRAIT);
        assertThat(document)
                .containsEntry("nom", "extrait-naissance.pdf")
                .containsEntry("type", "application/pdf")
                .containsEntry(
                        "sha256",
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(EXTRAIT)));
    }

    @Test
    void laValiditeProposeeEstRetenuePuisPlafonneeParLeRegistre() {
        JWTClaimsSet courte = ouvrir(envoyer(requete(ADULTE, avec(majeur(), "validite", "P7D"))));
        JWTClaimsSet longue =
                ouvrir(envoyer(requete(ADULTE, avec(majeur(), "validite", "P3650D"))));

        assertThat(validite(courte)).isEqualTo(Duration.ofDays(7));
        assertThat(validite(longue)).isEqualTo(Duration.ofDays(180));
    }

    @Test
    void laPasserelleEssaieUnJeuPourElleMeme() {
        // Essai d'un jeu depuis la console : la passerelle se désigne comme demandeur.
        Bicle chiffrementPasserelle = PKI.chiffrement(IdentiteMembre.PASSERELLE);
        ResponseEntity<String> reponse =
                envoyer(
                        requete(ADULTE, majeur())
                                .claim(
                                        "demandeur",
                                        demandeur(
                                                IdentiteMembre.PASSERELLE, chiffrementPasserelle)));

        assertThat(reponse.getStatusCode().value()).isEqualTo(200);
        Clock horloge = Clock.systemUTC();
        JWTClaimsSet attestation =
                new LecteurAttestation(
                                chiffrementPasserelle,
                                new VerificateurJws(
                                        PKI.autorite(),
                                        new RegistreJtiMemoire(10, horloge),
                                        horloge))
                        .ouvrir(
                                JsonMapper.builder()
                                        .build()
                                        .readTree(reponse.getBody())
                                        .get("attestation")
                                        .asString(),
                                REGISTRE);
        assertThat(attestation.getAudience()).containsExactly(IdentiteMembre.PASSERELLE.uri());
        assertThat(attestation.getClaim("resultat")).isEqualTo(Map.of("valeur", true));
    }

    @Test
    void aucunAutreComposantDuSocleNEstDemandeur() {
        ResponseEntity<String> reponse =
                envoyer(
                        requete(ADULTE, majeur())
                                .claim(
                                        "demandeur",
                                        demandeur(
                                                IdentiteMembre.CONSOLE,
                                                PKI.chiffrement(IdentiteMembre.CONSOLE))));

        assertThat(reponse.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void lAttestationEstIllisiblePourToutAutreQueLeService() throws Exception {
        String jwe =
                JsonMapper.builder()
                        .build()
                        .readTree(envoyer(requete(ADULTE, champs("nom"))).getBody())
                        .get("attestation")
                        .asString();
        Clock horloge = Clock.systemUTC();
        LecteurAttestation autreService =
                new LecteurAttestation(
                        PKI.chiffrement(SERVICE),
                        new VerificateurJws(
                                PKI.autorite(), new RegistreJtiMemoire(10, horloge), horloge));

        // Un JWE compact a cinq parties ; le contenu est chiffré, la passerelle ne lit rien.
        assertThat(JWEObject.parse(jwe).getState()).isEqualTo(JWEObject.State.ENCRYPTED);
        assertThat(jwe).doesNotContain(NOM);
        assertThatThrownBy(() -> autreService.ouvrir(jwe, REGISTRE))
                .isInstanceOfSatisfying(
                        JoseRefuse.class,
                        refus ->
                                assertThat(refus.motif())
                                        .isEqualTo(JoseRefuse.Motif.DECHIFFREMENT));
    }

    @Test
    void leJournalLocalDitQuiAQuoiDemandeSansAucuneValeur() {
        JWTClaimsSet.Builder question = requete(ADULTE, majeur());
        JWTClaimsSet.Builder identite = requete(ADULTE, champs("nom", "prenoms"));
        envoyer(question);
        envoyer(identite);

        Map<String, Object> ligne =
                jdbc.sql("SELECT * FROM journal WHERE id_echange = :id")
                        .param("id", question.build().getJWTID())
                        .query()
                        .singleRow();
        assertThat(ligne)
                .containsEntry("demandeur", SERVICE.uri())
                .containsEntry("demarche", "concours-direct")
                .containsEntry("finalite", "Vérifier les conditions du concours.")
                .containsEntry("jeu", "etat-civil.question")
                .containsEntry("niveau", "STATUT")
                .containsEntry("objet", "date_naissance AGE_AU_MOINS")
                .containsEntry("pseudonyme", PSEUDONYME)
                .containsEntry("canal", "CODE")
                .containsEntry("resultat", "REUSSI");
        assertThat(ligne.get("horodatage")).isNotNull();
        assertThat(issueAuJournal(identite)).isEqualTo("REUSSI");
        String journalEntier =
                jdbc.sql("SELECT journal::text FROM journal").query(String.class).list().toString();
        assertThat(journalEntier)
                .doesNotContain(NOM)
                .doesNotContain("Awa Mariam")
                .doesNotContain(NAISSANCE)
                .doesNotContain(ADULTE);
    }

    @Test
    void leJournalLocalNeSeModifiePasEtNeSEffacePas() {
        envoyer(requete(ADULTE, majeur()));

        assertThatThrownBy(() -> jdbc.sql("UPDATE journal SET resultat = 'EFFACE'").update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal").update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE journal").update())
                .isInstanceOf(DataAccessException.class);
    }
}
