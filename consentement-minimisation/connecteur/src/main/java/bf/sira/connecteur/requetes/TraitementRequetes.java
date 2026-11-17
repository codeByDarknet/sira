package bf.sira.connecteur.requetes;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Attentes;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.commun.jose.ScelleurAttestation;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.connecteur.ProprietesConnecteur;
import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import bf.sira.connecteur.journal.JournalConnecteur;
import bf.sira.connecteur.politique.Politique;
import bf.sira.connecteur.registre.ClientRegistre;
import bf.sira.connecteur.regles.Chemin;
import bf.sira.connecteur.regles.MoteurRegles;
import bf.sira.connecteur.regles.Regle;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Répond à une requête de la passerelle : vérifie sa signature, applique la politique locale avant
 * de toucher au registre, calcule la réponse au niveau demandé, la scelle pour le service
 * demandeur, et trace l'issue dans le journal local.
 */
@Service
public class TraitementRequetes {

    private final IdentiteMembre registre;
    private final VerificateurJws verificateur;
    private final LecteurRequete lecteur;
    private final Politique politique;
    private final ClientRegistre client;
    private final MoteurRegles moteur;
    private final ScelleurAttestation scelleur;
    private final JournalConnecteur journal;
    private final Clock horloge;

    TraitementRequetes(
            ProprietesConnecteur proprietes,
            Signataire signataire,
            VerificateurJws verificateur,
            LecteurRequete lecteur,
            Politique politique,
            ClientRegistre client,
            MoteurRegles moteur,
            ScelleurAttestation scelleur,
            JournalConnecteur journal,
            Clock horloge) {
        this.registre = IdentiteMembre.registre(proprietes.registre().code());
        // Un connecteur muni du cachet d'un autre registre signerait en son nom : il ne démarre
        // pas.
        if (!registre.equals(signataire.identite())) {
            throw new IllegalStateException(
                    "le cachet n'est pas celui du registre configuré : " + registre);
        }
        this.verificateur = verificateur;
        this.lecteur = lecteur;
        this.politique = politique;
        this.client = client;
        this.moteur = moteur;
        this.scelleur = scelleur;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * @param jws la requête signée par la passerelle
     * @return l'attestation, signée par le registre et chiffrée pour le service
     */
    public String traiter(String jws) {
        JWTClaimsSet revendications = verificateur.verifier(jws, Attentes.requete(registre));
        Requete requete = lecteur.lire(revendications);
        String idEchange = revendications.getJWTID();
        try {
            String attestation = attester(requete, idEchange);
            // Pas de trace, pas de donnée : si le journal échoue, l'attestation n'est pas remise.
            journal.tracer(idEchange, requete, JournalConnecteur.REUSSI);
            return attestation;
        } catch (Refus refus) {
            journal.tracer(idEchange, requete, refus.motif().name());
            throw refus;
        } catch (JoseRefuse refus) {
            journal.tracer(idEchange, requete, JournalConnecteur.CERTIFICAT_REFUSE);
            throw refus;
        }
    }

    private String attester(Requete requete, String idEchange) {
        Requete.Jeu jeu = requete.jeu();
        IdentiteMembre service =
                IdentiteMembre.lire(requete.demandeur().membre())
                        .orElseThrow(() -> new Refus(Motif.JEU_INVALIDE));
        // Le registre n'est consulté que si l'on peut chiffrer pour ce service.
        ScelleurAttestation.Destinataire destinataire =
                scelleur.destinataire(requete.demandeur().certificatChiffrement(), service);
        Instant maintenant = horloge.instant();
        Instant expiration = maintenant.plus(politique.validite(jeu.validite()));
        JWTClaimsSet attestation =
                new JWTClaimsSet.Builder()
                        .issuer(registre.uri())
                        .audience(service.uri())
                        .jwtID(idEchange)
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(expiration))
                        .subject(requete.sujet().pseudonyme())
                        .claim("demarche", requete.demarche())
                        .claim("jeu", jeu.code())
                        .claim("niveau", jeu.niveau().name())
                        .claim("resultat", resultat(jeu, requete.sujet().identifiantUnique()))
                        .build();
        return scelleur.sceller(attestation, destinataire);
    }

    /** La politique locale est appliquée avant tout appel au registre. */
    private Map<String, Object> resultat(Requete.Jeu jeu, String identifiant) {
        return switch (jeu.niveau()) {
            case STATUT -> {
                exiger(jeu.regle() != null && jeu.champs() == null && jeu.document() == null);
                Requete.RegleRecue recue = jeu.regle();
                Regle regle = Regle.de(recue.champ(), recue.operateur(), recue.valeur());
                politique.autoriser(regle);
                yield Map.of("valeur", moteur.evaluer(regle, client.fiche(identifiant)));
            }
            case CHAMPS -> {
                exiger(jeu.champs() != null && jeu.regle() == null && jeu.document() == null);
                List<Chemin> champs = jeu.champs().stream().map(Chemin::lire).toList();
                politique.autoriser(champs);
                yield Map.of("champs", moteur.extraire(champs, client.fiche(identifiant)));
            }
            case DOCUMENT -> {
                exiger(jeu.document() != null && jeu.regle() == null && jeu.champs() == null);
                politique.autoriserDocument(jeu.document());
                byte[] contenu = client.document(identifiant, jeu.document());
                yield Map.of(
                        "document",
                        Map.of(
                                "nom",
                                jeu.document() + ".pdf",
                                "type",
                                "application/pdf",
                                "sha256",
                                sha256(contenu),
                                "contenu",
                                Base64.getEncoder().encodeToString(contenu)));
            }
        };
    }

    private static void exiger(boolean jeuCoherent) {
        if (!jeuCoherent) {
            throw new Refus(Motif.JEU_INVALIDE);
        }
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
