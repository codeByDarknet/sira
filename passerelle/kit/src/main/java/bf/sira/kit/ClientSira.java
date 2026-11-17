package bf.sira.kit;

import bf.sira.commun.crypto.TempsConstant;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.pki.Certificats;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tout ce dont un service a besoin pour parler à Sira : ouvrir une demande, suivre son état,
 * l'annuler, et lire les attestations reçues. Les appels partent en mTLS vers la passerelle ; les
 * attestations sont déchiffrées avec la clé de chiffrement du service puis vérifiées (signature du
 * registre, chaîne de certificats, destinataire, validité).
 */
public final class ClientSira {

    static final String EN_TETE_IDEMPOTENCE = "Idempotency-Key";

    private static final JsonMapper PROBLEMES = JsonMapper.builder().build();

    private final RestClient passerelle;
    private final LecteurAttestation lecteur;
    private final String certificatChiffrement;

    /**
     * @param passerelle client mTLS dont l'adresse de base est celle de la passerelle
     * @param certificatChiffrement certificat de chiffrement du service, joint à chaque demande
     */
    public ClientSira(
            RestClient passerelle,
            LecteurAttestation lecteur,
            X509Certificate certificatChiffrement) {
        this.passerelle =
                passerelle
                        .mutate()
                        .defaultStatusHandler(
                                HttpStatusCode::isError,
                                (requete, reponse) -> {
                                    throw refus(reponse.getStatusCode(), lireProbleme(reponse));
                                })
                        .build();
        this.lecteur = lecteur;
        this.certificatChiffrement = Certificats.versPem(certificatChiffrement);
    }

    private record CorpsDemande(
            @JsonUnwrapped NouvelleDemande demande,
            @JsonProperty("certificat_chiffrement") String certificatChiffrement) {}

    /**
     * Ouvre une demande.
     *
     * @param cleIdempotence identifiant de cette tentative, choisi et conservé par le service :
     *     rejouer l'appel avec la même clé ne crée pas une seconde demande
     * @throws ErreurPasserelle {@code 403} sans habilitation approuvée pour cette démarche
     */
    public DemandeCreee creerDemande(NouvelleDemande demande, String cleIdempotence) {
        if (cleIdempotence == null || cleIdempotence.isBlank()) {
            throw new IllegalArgumentException("clé d'idempotence obligatoire");
        }
        return passerelle
                .post()
                .uri("/v1/demandes")
                .header(EN_TETE_IDEMPOTENCE, cleIdempotence)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CorpsDemande(demande, certificatChiffrement))
                .retrieve()
                .body(DemandeCreee.class);
    }

    /** État d'une demande ouverte par les canaux {@code CODE} ou {@code NOTIFICATION}. */
    public EtatDemande lireDemande(UUID id) {
        return passerelle.get().uri("/v1/demandes/{id}", id).retrieve().body(EtatDemande.class);
    }

    /**
     * État d'une demande ouverte par le canal {@code REDIRECTION}.
     *
     * @param codeRetour le code reçu par le navigateur du citoyen au retour de l'écran d'accord ;
     *     sans lui la passerelle ne remet pas les attestations
     */
    public EtatDemande lireDemande(UUID id, String codeRetour) {
        return passerelle
                .get()
                .uri(adresse ->
                        adresse.path("/v1/demandes/{id}")
                                .queryParam("code_retour", "{code}")
                                .build(id, codeRetour))
                .retrieve()
                .body(EtatDemande.class);
    }

    public void annulerDemande(UUID id) {
        passerelle.delete().uri("/v1/demandes/{id}", id).retrieve().toBodilessEntity();
    }

    public Demarche decrireDemarche(String code) {
        return passerelle.get().uri("/v1/demarches/{code}", code).retrieve().body(Demarche.class);
    }

    /** Ouvre les attestations déjà disponibles d'une demande, pour chaque jeu de la démarche. */
    public List<Attestation> attestations(EtatDemande demande, Demarche demarche) {
        return demarche.jeux().stream()
                .flatMap(jeu -> ouvrir(demande, demarche.code(), jeu).stream())
                .toList();
    }

    /**
     * Déchiffre et vérifie l'attestation reçue pour un jeu de la demande. Elle n'est rendue que si
     * elle est signée par le registre qui détient ce jeu, et parle de ce citoyen, de cette
     * démarche, de ce jeu et de ce niveau : la passerelle qui la transporte ne peut ni la lire ni
     * la déplacer d'une demande à l'autre.
     *
     * @param demande l'état lu auprès de la passerelle ; il porte le pseudonyme du citoyen
     * @param demarche le code de la démarche au nom de laquelle la demande a été ouverte
     * @param jeu le jeu attendu, tel que décrit par la démarche ou la demande créée
     * @return vide tant qu'aucune attestation n'est arrivée pour ce jeu
     * @throws JoseRefuse si elle n'est pas chiffrée pour ce service, ou si sa signature, son
     *     émetteur, son destinataire ou sa validité sont refusés
     * @throws AttestationInvalide si elle ne concerne pas cette demande, ou si son contenu ne
     *     respecte pas le contrat (forme du résultat, empreinte d'un document)
     */
    public Optional<Attestation> ouvrir(EtatDemande demande, String demarche, Jeu jeu) {
        List<EtatDemande.Element> recus =
                demande.elements().stream()
                        .filter(element -> jeu.code().equals(element.jeu()))
                        .filter(element -> element.attestation() != null)
                        .toList();
        if (recus.isEmpty()) {
            return Optional.empty();
        }
        if (recus.size() > 1) {
            throw new AttestationInvalide("plusieurs attestations pour un même jeu");
        }
        Attestation attestation =
                lire(
                        lecteur.ouvrir(
                                recus.getFirst().attestation(),
                                IdentiteMembre.registre(jeu.registre())));
        boolean concerneLaDemande =
                demande.pseudonyme() != null
                        && TempsConstant.egaux(attestation.pseudonyme(), demande.pseudonyme())
                        && attestation.demarche().equals(demarche)
                        && attestation.jeu().equals(jeu.code())
                        && attestation.niveau() == jeu.niveau();
        if (!concerneLaDemande) {
            throw new AttestationInvalide("l'attestation ne concerne pas cette demande");
        }
        return Optional.of(attestation);
    }

    private static Attestation lire(JWTClaimsSet revendications) {
        try {
            Niveau niveau = niveau(revendications.getStringClaim("niveau"));
            return new Attestation(
                    revendications.getJWTID(),
                    IdentiteMembre.lire(revendications.getIssuer()).orElseThrow(),
                    exiger(revendications.getSubject(), "sub"),
                    exiger(revendications.getStringClaim("demarche"), "demarche"),
                    exiger(revendications.getStringClaim("jeu"), "jeu"),
                    niveau,
                    revendications.getIssueTime().toInstant(),
                    revendications.getExpirationTime().toInstant(),
                    resultat(niveau, revendications.getJSONObjectClaim("resultat")));
        } catch (ParseException e) {
            throw new AttestationInvalide("revendication d'un type inattendu");
        }
    }

    private static Niveau niveau(String valeur) {
        try {
            return Niveau.valueOf(exiger(valeur, "niveau"));
        } catch (IllegalArgumentException e) {
            throw new AttestationInvalide("niveau inconnu");
        }
    }

    private static String exiger(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new AttestationInvalide("revendication absente : " + nom);
        }
        return valeur;
    }

    /** La forme du résultat doit être celle du niveau annoncé, et aucune autre. */
    private static Resultat resultat(Niveau niveau, Map<String, Object> resultat) {
        if (resultat == null || resultat.size() != 1) {
            throw new AttestationInvalide("résultat absent ou ambigu");
        }
        return switch (niveau) {
            case STATUT -> {
                if (!(resultat.get("valeur") instanceof Boolean valeur)) {
                    throw new AttestationInvalide("un statut attend « valeur »");
                }
                yield new Resultat.Statut(valeur);
            }
            case CHAMPS -> {
                if (!(resultat.get("champs") instanceof Map<?, ?> champs)) {
                    throw new AttestationInvalide("des champs sont attendus");
                }
                yield new Resultat.Champs(enChamps(champs));
            }
            case DOCUMENT -> {
                if (!(resultat.get("document") instanceof Map<?, ?> document)) {
                    throw new AttestationInvalide("un document est attendu");
                }
                yield document(document);
            }
        };
    }

    private static Map<String, Object> enChamps(Map<?, ?> champs) {
        Map<String, Object> copie = new LinkedHashMap<>();
        champs.forEach((nom, valeur) -> {
            if (valeur != null) {
                copie.put(String.valueOf(nom), valeur);
            }
        });
        return copie;
    }

    private static Resultat.Document document(Map<?, ?> document) {
        if (!(document.get("nom") instanceof String nom)
                || !(document.get("type") instanceof String type)
                || !(document.get("sha256") instanceof String empreinte)
                || !(document.get("contenu") instanceof String contenuEncode)) {
            throw new AttestationInvalide("document incomplet");
        }
        byte[] contenu;
        try {
            contenu = Base64.getDecoder().decode(contenuEncode);
        } catch (IllegalArgumentException e) {
            throw new AttestationInvalide("contenu du document illisible");
        }
        if (!empreinteCorrespond(contenu, empreinte)) {
            throw new AttestationInvalide("l'empreinte du document ne correspond pas");
        }
        return new Resultat.Document(nom, type, contenu);
    }

    /**
     * L'empreinte annoncée est acceptée en hexadécimal ou en base64 : le contrat ne tranche pas.
     */
    private static boolean empreinteCorrespond(byte[] contenu, String annoncee) {
        byte[] calculee;
        try {
            calculee = MessageDigest.getInstance("SHA-256").digest(contenu);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return annoncee.equalsIgnoreCase(HexFormat.of().formatHex(calculee))
                || annoncee.equals(Base64.getEncoder().encodeToString(calculee))
                || annoncee.equals(
                        Base64.getUrlEncoder().withoutPadding().encodeToString(calculee));
    }

    private static ProblemDetail lireProbleme(ClientHttpResponse reponse) {
        try {
            return PROBLEMES.readValue(reponse.getBody(), ProblemDetail.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static ErreurPasserelle refus(HttpStatusCode statut, ProblemDetail probleme) {
        return new ErreurPasserelle(
                statut.value(),
                probleme != null && probleme.getType() != null
                        ? probleme.getType().toString()
                        : null,
                probleme != null ? probleme.getTitle() : null);
    }
}
