package bf.sira.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.ScelleurAttestation;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientSiraTest {

    private static final IdentiteMembre SERVICE = IdentiteMembre.service("concours");
    private static final IdentiteMembre REGISTRE = IdentiteMembre.registre("etat-civil");
    private static final UUID ID = UUID.fromString("0b8f1c1e-58a4-4f0e-9d53-2f2d9a3f7a10");

    private final PkiDeTest pki = PkiDeTest.creer();
    private final Bicle chiffrement = pki.chiffrement(SERVICE);
    private final ScelleurAttestation registre =
            new ScelleurAttestation(
                    new Signataire(pki.cachet(REGISTRE)), pki.autorite(), Clock.systemUTC());
    private final RestClient.Builder constructeur =
            RestClient.builder().baseUrl("https://passerelle:8443");
    private final MockRestServiceServer passerelle =
            MockRestServiceServer.bindTo(constructeur).build();
    private final ClientSira sira =
            new ClientSira(
                    constructeur.build(),
                    new LecteurAttestation(
                            chiffrement,
                            new VerificateurJws(
                                    pki.autorite(), new RegistreJtiMemoire(), Clock.systemUTC())),
                    chiffrement.certificat());

    private static final String PSEUDONYME = "pseudonyme-chez-concours";
    private static final String DEMARCHE = "concours-direct";
    private static final String JEU = "etat-civil.majeur";

    private String attestation(
            IdentiteMembre destinataire, Bicle cle, String niveau, Object resultat) {
        return attestation(registre, destinataire, cle, niveau, resultat, revendications -> {});
    }

    private String attestation(
            ScelleurAttestation signataire,
            IdentiteMembre destinataire,
            Bicle cle,
            String niveau,
            Object resultat,
            Consumer<JWTClaimsSet.Builder> retouches) {
        Instant maintenant = Instant.now();
        IdentiteMembre emetteur =
                signataire == registre ? REGISTRE : IdentiteMembre.registre("diplomes");
        JWTClaimsSet.Builder revendications =
                new JWTClaimsSet.Builder()
                        .issuer(emetteur.uri())
                        .audience(destinataire.uri())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plusSeconds(3600)))
                        .subject(PSEUDONYME)
                        .claim("demarche", DEMARCHE)
                        .claim("jeu", JEU)
                        .claim("niveau", niveau)
                        .claim("resultat", resultat);
        retouches.accept(revendications);
        return signataire.sceller(
                revendications.build(), Certificats.versPem(cle.certificat()), destinataire);
    }

    private String attestation(String niveau, Object resultat) {
        return attestation(SERVICE, chiffrement, niveau, resultat);
    }

    private String attestation(Consumer<JWTClaimsSet.Builder> retouches) {
        return attestation(
                registre, SERVICE, chiffrement, "STATUT", Map.of("valeur", true), retouches);
    }

    private static EtatDemande etat(String pseudonyme, String jwe) {
        return new EtatDemande(
                ID,
                EtatDemandeCode.COMPLETE,
                pseudonyme,
                List.of(new EtatDemande.Element(JEU, EtatElement.OBTENU, jwe, null)));
    }

    private static Jeu jeu(Niveau niveau) {
        return new Jeu(JEU, "Vous êtes majeur", niveau, "etat-civil");
    }

    /** Ouvre l'attestation comme le ferait le service, pour le jeu majeur de sa démarche. */
    private Attestation ouvrir(String jwe, Niveau niveau) {
        return sira.ouvrir(etat(PSEUDONYME, jwe), DEMARCHE, jeu(niveau)).orElseThrow();
    }

    private Attestation ouvrir(String jwe) {
        return ouvrir(jwe, Niveau.STATUT);
    }

    @Test
    void redirectionEtCodePeuventDesignerLaPersonne() {
        assertThat(NouvelleDemande.parRedirection("concours-direct", "http://r", "990000000001"))
                .isEqualTo(
                        new NouvelleDemande(
                                "concours-direct",
                                Canal.REDIRECTION,
                                "http://r",
                                "990000000001",
                                null));
        assertThat(NouvelleDemande.parCode("concours-direct", "990000000001"))
                .isEqualTo(
                        new NouvelleDemande(
                                "concours-direct", Canal.CODE, null, "990000000001", null));
        assertThat(NouvelleDemande.parRedirection("concours-direct", "http://r", null))
                .isEqualTo(NouvelleDemande.parRedirection("concours-direct", "http://r"));
        assertThat(NouvelleDemande.parCode("concours-direct", null))
                .isEqualTo(NouvelleDemande.parCode("concours-direct"));
    }

    @Test
    void ouvrirUneDemandeJointLeCertificatDeChiffrementEtLaCleDIdempotence() {
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demandes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "tentative-1"))
                .andExpect(jsonPath("$.demarche").value("concours-direct"))
                .andExpect(jsonPath("$.canal").value("REDIRECTION"))
                .andExpect(jsonPath("$.retour_url").value("http://concours.test/retour"))
                .andExpect(
                        jsonPath("$.certificat_chiffrement")
                                .value(Certificats.versPem(chiffrement.certificat())))
                .andRespond(
                        withStatus(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"id":"%s","etat":"EN_ATTENTE_ACCORD",
                                         "url_accord":"http://citoyen.test/accord/abc",
                                         "code_court":"KLPM-4821","code_verification":"492113",
                                         "expire_le":"2026-11-16T10:05:00Z",
                                         "jeux":[{"code":"etat-civil.majeur","libelle":"Vous êtes majeur",
                                                  "niveau":"STATUT","registre":"etat-civil"}]}
                                        """
                                                .formatted(ID)));

        DemandeCreee creee =
                sira.creerDemande(
                        NouvelleDemande.parRedirection(
                                "concours-direct", "http://concours.test/retour"),
                        "tentative-1");

        passerelle.verify();
        assertThat(creee.id()).isEqualTo(ID);
        assertThat(creee.etat()).isEqualTo(EtatDemandeCode.EN_ATTENTE_ACCORD);
        assertThat(creee.urlAccord()).isEqualTo("http://citoyen.test/accord/abc");
        assertThat(creee.codeCourt()).isEqualTo("KLPM-4821");
        assertThat(creee.codeVerification()).isEqualTo("492113");
        assertThat(creee.expireLe()).isEqualTo(Instant.parse("2026-11-16T10:05:00Z"));
        assertThat(creee.jeux())
                .containsExactly(
                        new Jeu(
                                "etat-civil.majeur",
                                "Vous êtes majeur",
                                Niveau.STATUT,
                                "etat-civil"));
        assertThatThrownBy(() -> sira.creerDemande(NouvelleDemande.parCode("concours-direct"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lireUneDemandePresenteLeCodeDeRetourEtOuvreLesAttestations() {
        String jwe = attestation("STATUT", Map.of("valeur", true));
        passerelle
                .expect(
                        requestTo(
                                "https://passerelle:8443/v1/demandes/"
                                        + ID
                                        + "?code_retour=a%26b%3Dc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {"id":"%s","etat":"PARTIELLE","pseudonyme":"pseudonyme-chez-concours",
                                 "elements":[
                                  {"jeu":"etat-civil.majeur","etat":"OBTENU","attestation":"%s"},
                                  {"jeu":"casier.neant","etat":"A_TRAITER",
                                   "prochaine_tentative_le":"2026-11-16T10:00:30Z"}]}
                                """
                                        .formatted(ID, jwe),
                                MediaType.APPLICATION_JSON));

        EtatDemande etat = sira.lireDemande(ID, "a&b=c");
        List<Attestation> attestations =
                sira.attestations(
                        etat,
                        new Demarche(
                                DEMARCHE,
                                "Concours direct",
                                List.of(
                                        jeu(Niveau.STATUT),
                                        new Jeu(
                                                "casier.neant",
                                                "Casier vierge",
                                                Niveau.STATUT,
                                                "casier"))));

        passerelle.verify();
        assertThat(etat.etat()).isEqualTo(EtatDemandeCode.PARTIELLE);
        assertThat(etat.etat().estFinal()).isFalse();
        assertThat(etat.elements()).hasSize(2);
        assertThat(etat.elements().get(1).etat()).isEqualTo(EtatElement.A_TRAITER);
        assertThat(etat.elements().get(1).prochaineTentativeLe())
                .isEqualTo(Instant.parse("2026-11-16T10:00:30Z"));
        assertThat(attestations).hasSize(1);
        Attestation attestation = attestations.getFirst();
        assertThat(attestation.registre()).isEqualTo(REGISTRE);
        assertThat(attestation.pseudonyme()).isEqualTo("pseudonyme-chez-concours");
        assertThat(attestation.demarche()).isEqualTo("concours-direct");
        assertThat(attestation.jeu()).isEqualTo("etat-civil.majeur");
        assertThat(attestation.niveau()).isEqualTo(Niveau.STATUT);
        assertThat(attestation.resultat()).isEqualTo(new Resultat.Statut(true));
    }

    @Test
    void lireSansCodeDeRetourEtAnnuler() {
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demandes/" + ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"%s\",\"etat\":\"EN_ATTENTE_ACCORD\"}".formatted(ID),
                                MediaType.APPLICATION_JSON));
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demandes/" + ID))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        EtatDemande etat = sira.lireDemande(ID);
        sira.annulerDemande(ID);

        passerelle.verify();
        assertThat(etat.elements()).isEmpty();
        assertThat(
                        sira.attestations(
                                etat,
                                new Demarche(DEMARCHE, "Concours", List.of(jeu(Niveau.STATUT)))))
                .isEmpty();
    }

    @Test
    void decrireUneDemarche() {
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demarches/concours-direct"))
                .andRespond(
                        withSuccess(
                                """
                                {"code":"concours-direct","libelle":"Concours direct","champ_futur":1,
                                 "jeux":[{"code":"diplomes.bac","libelle":"Votre baccalauréat",
                                          "niveau":"CHAMPS","registre":"diplomes"}]}
                                """,
                                MediaType.APPLICATION_JSON));

        Demarche demarche = sira.decrireDemarche("concours-direct");

        assertThat(demarche.libelle()).isEqualTo("Concours direct");
        assertThat(demarche.jeux().getFirst().niveau()).isEqualTo(Niveau.CHAMPS);
    }

    @Test
    void unRefusDeLaPasserelleDonneSonStatutEtSonType() {
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demandes"))
                .andRespond(
                        withStatus(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                                .body(
                                        "{\"type\":\"urn:sira:erreur:habilitation-absente\","
                                                + "\"title\":\"Habilitation absente\",\"status\":403}"));
        passerelle
                .expect(requestTo("https://passerelle:8443/v1/demandes/" + ID))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>proxy</html>"));

        assertThatThrownBy(
                        () -> sira.creerDemande(NouvelleDemande.parCode("concours-direct"), "t-2"))
                .isInstanceOfSatisfying(
                        ErreurPasserelle.class,
                        erreur -> {
                            assertThat(erreur.statut()).isEqualTo(403);
                            assertThat(erreur.type())
                                    .isEqualTo("urn:sira:erreur:habilitation-absente");
                        });
        assertThatThrownBy(() -> sira.lireDemande(ID))
                .isInstanceOfSatisfying(
                        ErreurPasserelle.class,
                        erreur -> {
                            assertThat(erreur.statut()).isEqualTo(502);
                            assertThat(erreur.type()).isNull();
                        });
    }

    @Test
    void desChampsSontRemisTelsQuAttestes() {
        Attestation attestation =
                ouvrir(
                        attestation(
                                "CHAMPS",
                                Map.of("champs", Map.of("nom", "OUEDRAOGO", "prenoms", "Awa"))),
                        Niveau.CHAMPS);

        assertThat(attestation.resultat())
                .isEqualTo(new Resultat.Champs(Map.of("nom", "OUEDRAOGO", "prenoms", "Awa")));
    }

    @Test
    void unDocumentEstRemisSiSonEmpreinteCorrespond() throws Exception {
        byte[] piece = "%PDF-1.7 extrait d'acte".getBytes(StandardCharsets.UTF_8);
        String empreinte =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(piece));
        Map<String, Object> document =
                Map.of(
                        "nom",
                        "extrait.pdf",
                        "type",
                        "application/pdf",
                        "sha256",
                        empreinte,
                        "contenu",
                        Base64.getEncoder().encodeToString(piece));

        Attestation attestation =
                ouvrir(attestation("DOCUMENT", Map.of("document", document)), Niveau.DOCUMENT);

        assertThat(attestation.resultat())
                .isInstanceOfSatisfying(
                        Resultat.Document.class,
                        recu -> {
                            assertThat(recu.nom()).isEqualTo("extrait.pdf");
                            assertThat(recu.type()).isEqualTo("application/pdf");
                            assertThat(recu.contenu()).isEqualTo(piece);
                        });
    }

    @Test
    void unDocumentDontLEmpreinteDiffereEstRefuse() {
        Map<String, Object> document =
                Map.of(
                        "nom",
                        "extrait.pdf",
                        "type",
                        "application/pdf",
                        "sha256",
                        "00".repeat(32),
                        "contenu",
                        Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        String jwe = attestation("DOCUMENT", Map.of("document", document));

        assertThatThrownBy(() -> ouvrir(jwe, Niveau.DOCUMENT))
                .isInstanceOf(AttestationInvalide.class);
    }

    @Test
    void unResultatQuiNEstPasCeluiDuNiveauAnnonceEstRefuse() {
        String plusQueLeNiveau = attestation("STATUT", Map.of("champs", Map.of("nom", "X")));
        String ambigu = attestation("STATUT", Map.of("valeur", true, "champs", Map.of("nom", "X")));
        String niveauInconnu = attestation("TOUT", Map.of("valeur", true));

        assertThatThrownBy(() -> ouvrir(plusQueLeNiveau)).isInstanceOf(AttestationInvalide.class);
        assertThatThrownBy(() -> ouvrir(ambigu)).isInstanceOf(AttestationInvalide.class);
        assertThatThrownBy(() -> ouvrir(niveauInconnu)).isInstanceOf(AttestationInvalide.class);
    }

    @Test
    void uneAttestationDestineeAUnAutreServiceEstRefusee() {
        IdentiteMembre autre = IdentiteMembre.service("autre");
        String pourUnAutre =
                attestation(autre, pki.chiffrement(autre), "STATUT", Map.of("valeur", true));

        assertThatThrownBy(() -> ouvrir(pourUnAutre)).isInstanceOf(JoseRefuse.class);
    }

    @Test
    void uneAttestationDUneAutreAutoriteEstRefusee() {
        PkiDeTest etrangere = PkiDeTest.creer();
        ScelleurAttestation fauxRegistre =
                new ScelleurAttestation(
                        new Signataire(etrangere.cachet(REGISTRE)),
                        pki.autorite(),
                        Clock.systemUTC());
        Instant maintenant = Instant.now();
        String jwe =
                fauxRegistre.sceller(
                        new JWTClaimsSet.Builder()
                                .issuer(REGISTRE.uri())
                                .audience(SERVICE.uri())
                                .jwtID("x")
                                .issueTime(Date.from(maintenant))
                                .expirationTime(Date.from(maintenant.plusSeconds(60)))
                                .build(),
                        Certificats.versPem(chiffrement.certificat()),
                        SERVICE);

        assertThatThrownBy(() -> ouvrir(jwe))
                .isInstanceOfSatisfying(
                        JoseRefuse.class,
                        refus -> assertThat(refus.motif()).isEqualTo(JoseRefuse.Motif.CERTIFICAT));
    }

    @Test
    void uneAttestationNEstLueQueDuRegistreQuiDetientLeJeu() {
        ScelleurAttestation diplomes =
                new ScelleurAttestation(
                        new Signataire(pki.cachet(IdentiteMembre.registre("diplomes"))),
                        pki.autorite(),
                        Clock.systemUTC());
        String jwe =
                attestation(
                        diplomes,
                        SERVICE,
                        chiffrement,
                        "STATUT",
                        Map.of("valeur", true),
                        revendications -> {});

        assertThatThrownBy(() -> ouvrir(jwe))
                .isInstanceOfSatisfying(
                        JoseRefuse.class,
                        refus -> assertThat(refus.motif()).isEqualTo(JoseRefuse.Motif.IDENTITE));
    }

    @Test
    void uneAttestationQuiNeConcernePasCetteDemandeEstRefusee() {
        String autreCitoyen = attestation(r -> r.subject("pseudonyme-d-un-autre"));
        String autreDemarche = attestation(r -> r.claim("demarche", "bourse"));
        String autreJeu = attestation(r -> r.claim("jeu", "etat-civil.nationalite"));
        String bonne = attestation(r -> {});

        for (String jwe : List.of(autreCitoyen, autreDemarche, autreJeu)) {
            assertThatThrownBy(() -> ouvrir(jwe)).isInstanceOf(AttestationInvalide.class);
        }
        assertThatThrownBy(() -> ouvrir(bonne, Niveau.CHAMPS))
                .isInstanceOf(AttestationInvalide.class);
        assertThatThrownBy(() -> sira.ouvrir(etat(null, bonne), DEMARCHE, jeu(Niveau.STATUT)))
                .isInstanceOf(AttestationInvalide.class);
        assertThat(ouvrir(bonne).pseudonyme()).isEqualTo(PSEUDONYME);
    }

    @Test
    void unJeuSansAttestationNeRendRien() {
        assertThat(sira.ouvrir(etat(PSEUDONYME, null), DEMARCHE, jeu(Niveau.STATUT))).isEmpty();
    }
}
