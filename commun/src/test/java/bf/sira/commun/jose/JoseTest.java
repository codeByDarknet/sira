package bf.sira.commun.jose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse.Motif;
import bf.sira.commun.pki.Bicle;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Signature des requêtes et scellement des attestations (contrat §5.4, §5.5 et §8). */
class JoseTest {

    private static final Instant MAINTENANT = Instant.now();
    private static final Clock HORLOGE = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private static final IdentiteMembre REGISTRE = IdentiteMembre.registre("etat-civil");
    private static final IdentiteMembre SERVICE = IdentiteMembre.service("demo-bourse");

    private final PkiDeTest pki = PkiDeTest.creer();
    private final Signataire passerelle = new Signataire(pki.cachet(IdentiteMembre.PASSERELLE));
    private final Signataire registre = new Signataire(pki.cachet(REGISTRE));
    private final Bicle chiffrementService = pki.chiffrement(SERVICE);
    private final VerificateurJws verificateur = verificateur(HORLOGE);
    private final ScelleurAttestation scelleur =
            new ScelleurAttestation(registre, pki.autorite(), HORLOGE);
    private final LecteurAttestation lecteur =
            new LecteurAttestation(chiffrementService, verificateur);

    private VerificateurJws verificateur(Clock horloge) {
        return new VerificateurJws(pki.autorite(), new RegistreJtiMemoire(100, horloge), horloge);
    }

    private static JWTClaimsSet.Builder requete(IdentiteMembre emetteur) {
        return new JWTClaimsSet.Builder()
                .issuer(emetteur.uri())
                .audience(REGISTRE.uri())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(MAINTENANT))
                .expirationTime(Date.from(MAINTENANT.plusSeconds(60)))
                .claim("demandeur", SERVICE.uri());
    }

    private static JWTClaimsSet.Builder attestation() {
        return new JWTClaimsSet.Builder()
                .issuer(REGISTRE.uri())
                .audience(SERVICE.uri())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(MAINTENANT))
                .expirationTime(Date.from(MAINTENANT.plus(Duration.ofHours(1))))
                .subject("pseudonyme-du-citoyen")
                .claim("resultat", Map.of("valeur", true));
    }

    private String requeteSignee() {
        return passerelle.signer(TypesJose.REQUETE, requete(IdentiteMembre.PASSERELLE).build());
    }

    private String attestationScellee() {
        return scelleur.sceller(
                attestation().build(),
                Certificats.versPem(chiffrementService.certificat()),
                SERVICE);
    }

    private static void assertRefus(Motif motif, ThrowingCallable appel) {
        assertThatThrownBy(appel)
                .isInstanceOfSatisfying(
                        JoseRefuse.class, refus -> assertThat(refus.motif()).isEqualTo(motif));
    }

    @Test
    void uneRequeteDeLaPasserelleEstVerifiee() {
        JWTClaimsSet lues = verificateur.verifier(requeteSignee(), Attentes.requete(REGISTRE));

        assertThat(lues.getIssuer()).isEqualTo("urn:sira:socle:passerelle");
        assertThat(lues.getClaim("demandeur")).isEqualTo(SERVICE.uri());
    }

    @Test
    void uneRequeteRejoueeEstRefusee() {
        String jws = requeteSignee();
        verificateur.verifier(jws, Attentes.requete(REGISTRE));

        assertRefus(Motif.REJEU, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void uneRequeteExpireeEstRefusee() {
        String jws = requeteSignee();
        VerificateurJws plusTard =
                verificateur(Clock.fixed(MAINTENANT.plusSeconds(66), ZoneOffset.UTC));

        assertRefus(Motif.VALIDITE, () -> plusTard.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void uneRequeteTropLongueOuSansEcheanceEstRefusee() {
        String longue =
                passerelle.signer(
                        TypesJose.REQUETE,
                        requete(IdentiteMembre.PASSERELLE)
                                .expirationTime(Date.from(MAINTENANT.plusSeconds(600)))
                                .build());
        String sansEcheance =
                passerelle.signer(
                        TypesJose.REQUETE,
                        requete(IdentiteMembre.PASSERELLE).expirationTime(null).build());

        assertRefus(
                Motif.VALIDITE, () -> verificateur.verifier(longue, Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.VALIDITE,
                () -> verificateur.verifier(sansEcheance, Attentes.requete(REGISTRE)));
    }

    @Test
    void unSignataireDUneAutreAutoriteEstRefuse() {
        Signataire faussePasserelle =
                new Signataire(PkiDeTest.creer().cachet(IdentiteMembre.PASSERELLE));
        String jws =
                faussePasserelle.signer(
                        TypesJose.REQUETE, requete(IdentiteMembre.PASSERELLE).build());

        assertRefus(Motif.CERTIFICAT, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void unMembreQuiNEstPasLaPasserelleNePeutPasSignerUneRequete() {
        Signataire service = new Signataire(pki.cachet(SERVICE));
        Signataire console = new Signataire(pki.cachet(IdentiteMembre.CONSOLE));
        String duService = service.signer(TypesJose.REQUETE, requete(SERVICE).build());
        String deLaConsole =
                console.signer(TypesJose.REQUETE, requete(IdentiteMembre.CONSOLE).build());

        assertRefus(
                Motif.IDENTITE, () -> verificateur.verifier(duService, Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.IDENTITE,
                () -> verificateur.verifier(deLaConsole, Attentes.requete(REGISTRE)));
    }

    @Test
    void unCertificatSansIdentiteSiraEstRefuse() throws Exception {
        Bicle anonyme = pki.cachet("https://exemple.bf/passerelle", MAINTENANT);
        String jws = signerSansControle(anonyme, requete(IdentiteMembre.PASSERELLE).build());

        assertRefus(Motif.IDENTITE, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void unIssDifferentDuCertificatEstRefuse() throws Exception {
        Bicle cachetPasserelle = pki.cachet(IdentiteMembre.PASSERELLE);
        String jws = signerSansControle(cachetPasserelle, requete(IdentiteMembre.CONSOLE).build());

        assertRefus(Motif.IDENTITE, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void unCertificatDeSignataireExpireEstRefuse() throws Exception {
        Bicle perime =
                pki.cachet(IdentiteMembre.PASSERELLE.uri(), MAINTENANT.minus(Duration.ofDays(3)));
        String jws = signerSansControle(perime, requete(IdentiteMembre.PASSERELLE).build());

        assertRefus(Motif.CERTIFICAT, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void uneCleDeTransportOuDeChiffrementNeSignePas() throws Exception {
        String avecTls =
                signerSansControle(
                        pki.tls(IdentiteMembre.PASSERELLE),
                        requete(IdentiteMembre.PASSERELLE).build());
        String avecChiffrement =
                signerSansControle(
                        pki.chiffrement(IdentiteMembre.PASSERELLE),
                        requete(IdentiteMembre.PASSERELLE).build());

        assertRefus(
                Motif.CERTIFICAT, () -> verificateur.verifier(avecTls, Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.CERTIFICAT,
                () -> verificateur.verifier(avecChiffrement, Attentes.requete(REGISTRE)));
    }

    @Test
    void uneRequetePourUnAutreRegistreEstRefusee() {
        String jws = requeteSignee();

        assertRefus(
                Motif.AUDIENCE,
                () ->
                        verificateur.verifier(
                                jws, Attentes.requete(IdentiteMembre.registre("fiscalite"))));
    }

    @Test
    void uneSignatureAltereeEstRefusee() {
        String jws = requeteSignee();
        int dernier = jws.length() - 2;
        String alteree =
                jws.substring(0, dernier)
                        + (jws.charAt(dernier) == 'A' ? 'B' : 'A')
                        + jws.substring(dernier + 1);
        String[] parties = requeteSignee().split("\\.");
        String autreContenu = parties[0] + "." + jws.split("\\.")[1] + "." + parties[2];

        assertRefus(
                Motif.SIGNATURE, () -> verificateur.verifier(alteree, Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.SIGNATURE,
                () -> verificateur.verifier(autreContenu, Attentes.requete(REGISTRE)));
    }

    @Test
    void unObjetDUnAutreTypeOuIllisibleEstRefuse() {
        String journal =
                passerelle.signer(
                        TypesJose.POINT_DE_CONTROLE, requete(IdentiteMembre.PASSERELLE).build());

        assertRefus(Motif.FORMAT, () -> verificateur.verifier(journal, Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.FORMAT, () -> verificateur.verifier("abc.def", Attentes.requete(REGISTRE)));
        assertRefus(
                Motif.FORMAT,
                () ->
                        verificateur.verifier(
                                "eyJhbGciOiJub25lIn0.e30.", Attentes.requete(REGISTRE)));
    }

    @Test
    void leSignataireNeSignePasAuNomDUnAutre() {
        assertThatThrownBy(() -> passerelle.signer(TypesJose.REQUETE, requete(REGISTRE).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uneAttestationScelleeEstLueParSonDestinataire() {
        String jwe = attestationScellee();

        JWTClaimsSet lues = lecteur.ouvrir(jwe, REGISTRE);

        assertThat(jwe.split("\\.")).hasSize(5);
        assertThat(jwe).doesNotContain("pseudonyme");
        assertThat(lues.getIssuer()).isEqualTo(REGISTRE.uri());
        assertThat(lues.getSubject()).isEqualTo("pseudonyme-du-citoyen");
        assertThat(lues.getClaim("resultat")).isEqualTo(Map.of("valeur", true));
        // Une attestation se relit pendant sa validité.
        assertThat(lecteur.ouvrir(jwe, REGISTRE).getJWTID()).isEqualTo(lues.getJWTID());
    }

    @Test
    void uneAttestationNEstLueQueDuRegistreAttendu() {
        String jwe = attestationScellee();

        assertRefus(Motif.IDENTITE, () -> lecteur.ouvrir(jwe, IdentiteMembre.registre("diplomes")));
        assertThatThrownBy(() -> lecteur.ouvrir(jwe, SERVICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unObjetAPlusieursAudiencesEstRefuse() throws Exception {
        String jws =
                signerSansControle(
                        pki.cachet(IdentiteMembre.PASSERELLE),
                        requete(IdentiteMembre.PASSERELLE)
                                .audience(
                                        List.of(
                                                REGISTRE.uri(),
                                                IdentiteMembre.registre("fiscalite").uri()))
                                .build());

        assertRefus(Motif.AUDIENCE, () -> verificateur.verifier(jws, Attentes.requete(REGISTRE)));
    }

    @Test
    void leDestinataireEstValideAvantDeSceller() {
        String etranger = Certificats.versPem(PkiDeTest.creer().chiffrement(SERVICE).certificat());
        String sansUsage = Certificats.versPem(pki.sansUsage(SERVICE).certificat());

        assertRefus(Motif.CERTIFICAT, () -> scelleur.destinataire(etranger, SERVICE));
        assertRefus(Motif.CERTIFICAT, () -> scelleur.destinataire(sansUsage, SERVICE));
        ScelleurAttestation.Destinataire destinataire =
                scelleur.destinataire(
                        Certificats.versPem(chiffrementService.certificat()), SERVICE);
        assertThat(
                        lecteur.ouvrir(
                                        scelleur.sceller(attestation().build(), destinataire),
                                        REGISTRE)
                                .getSubject())
                .isEqualTo("pseudonyme-du-citoyen");
    }

    @Test
    void uneAttestationNEstPasLisibleAvecUneAutreCle() {
        String jwe = attestationScellee();
        LecteurAttestation autreCle =
                new LecteurAttestation(pki.chiffrement(SERVICE), verificateur);

        assertRefus(Motif.DECHIFFREMENT, () -> autreCle.ouvrir(jwe, REGISTRE));
        assertRefus(Motif.FORMAT, () -> lecteur.ouvrir("pas-un-jwe", REGISTRE));
    }

    @Test
    void uneAttestationExpireeEstRefusee() {
        String jwe = attestationScellee();
        LecteurAttestation plusTard =
                new LecteurAttestation(
                        chiffrementService,
                        verificateur(
                                Clock.fixed(MAINTENANT.plus(Duration.ofHours(2)), ZoneOffset.UTC)));

        assertRefus(Motif.VALIDITE, () -> plusTard.ouvrir(jwe, REGISTRE));
    }

    @Test
    void uneAttestationSigneeParUnNonRegistreEstRefusee() {
        ScelleurAttestation faux =
                new ScelleurAttestation(
                        new Signataire(pki.cachet(IdentiteMembre.service("autre"))),
                        pki.autorite(),
                        HORLOGE);
        String jwe =
                faux.sceller(
                        attestation().issuer("urn:sira:service:autre").build(),
                        Certificats.versPem(chiffrementService.certificat()),
                        SERVICE);

        assertRefus(Motif.IDENTITE, () -> lecteur.ouvrir(jwe, REGISTRE));
    }

    @Test
    void onNeChiffrePasPourUnCertificatDUneAutreAutorite() {
        String etranger = Certificats.versPem(PkiDeTest.creer().chiffrement(SERVICE).certificat());

        assertRefus(
                Motif.CERTIFICAT, () -> scelleur.sceller(attestation().build(), etranger, SERVICE));
        assertRefus(
                Motif.CERTIFICAT,
                () -> scelleur.sceller(attestation().build(), "illisible", SERVICE));
    }

    @Test
    void onNeChiffrePasPourUnAutreMembreQueLeDestinataire() {
        String autreService =
                Certificats.versPem(pki.chiffrement(IdentiteMembre.service("autre")).certificat());

        assertRefus(
                Motif.IDENTITE,
                () -> scelleur.sceller(attestation().build(), autreService, SERVICE));
    }

    @Test
    void onNeChiffrePasPourUneCleDeCachetOuDeTransport() {
        String cachet = Certificats.versPem(pki.cachet(SERVICE).certificat());
        String tls = Certificats.versPem(pki.tls(SERVICE).certificat());

        assertRefus(
                Motif.CERTIFICAT, () -> scelleur.sceller(attestation().build(), cachet, SERVICE));
        assertRefus(Motif.CERTIFICAT, () -> scelleur.sceller(attestation().build(), tls, SERVICE));
    }

    /** Signe comme le ferait un émetteur malveillant, sans les garde-fous de {@link Signataire}. */
    private static String signerSansControle(Bicle bicle, JWTClaimsSet revendications)
            throws Exception {
        SignedJWT jws =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .type(new com.nimbusds.jose.JOSEObjectType(TypesJose.REQUETE))
                                .x509CertChain(
                                        List.of(Base64.encode(bicle.certificat().getEncoded())))
                                .build(),
                        revendications);
        jws.sign(new ECDSASigner((ECPrivateKey) bicle.clePrivee()));
        return jws.serialize();
    }
}
