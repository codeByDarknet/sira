package bf.sira.connecteur.registre;

import bf.sira.connecteur.ProprietesConnecteur;
import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import bf.sira.connecteur.registre.Raccordement.Authentification;
import bf.sira.connecteur.registre.Raccordement.Lecture;
import bf.sira.connecteur.registre.Raccordement.Methode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seul point de contact avec le registre existant (contrat §9). Comment le joindre et le lire est
 * décrit par un fichier de raccordement ({@link Raccordement}) ; sans fichier, le connecteur lit
 * l'API interne du registre de démonstration à l'adresse {@code sira.connecteur.registre.url}.
 *
 * <p>Délais et tailles bornés, aucune redirection suivie, https exigé hors démonstration, hôte
 * contrôlé ({@link GardeAdresses}). Une panne ou une réponse inattendue devient « registre
 * indisponible », sans rien dire de plus à l'appelant.
 */
@Component
public class ClientRegistre {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int TAILLE_SECRET_MAX = 4096;

    private final Raccordement raccordement;
    private final RestClient client;
    private final int tailleFicheMax;
    private final int tailleDocumentMax;

    public ClientRegistre(ProprietesConnecteur proprietes) {
        ProprietesConnecteur.Registre registre = proprietes.registre();
        this.raccordement = raccordement(registre);
        Authentification authentification = raccordement.authentification();
        HttpClientSettings reglages =
                HttpClientSettings.defaults()
                        .withRedirects(HttpRedirects.DONT_FOLLOW)
                        .withTimeouts(registre.delaiConnexion(), registre.delaiLecture());
        SslBundle tls = tls(authentification);
        if (tls != null) {
            reglages = reglages.withSslBundle(tls);
        }
        RestClient.Builder constructeur =
                RestClient.builder()
                        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(reglages))
                        .baseUrl(raccordement.adresse());
        switch (authentification.type()) {
            case JETON ->
                    constructeur.defaultHeader(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + secret(authentification.fichier()));
            case CLE_API ->
                    constructeur.defaultHeader(
                            authentification.enTete(), secret(authentification.fichier()));
            case AUCUNE, MTLS -> {}
        }
        this.client = constructeur.build();
        this.tailleFicheMax = Math.toIntExact(registre.tailleFicheMax().toBytes());
        this.tailleDocumentMax = Math.toIntExact(registre.tailleDocumentMax().toBytes());
    }

    /**
     * @param identifiant identifiant unique, déjà validé (lettres, chiffres et tirets)
     * @return la fiche de la personne, réduite aux champs de la correspondance s'il y en a une :
     *     elle ne quitte jamais le connecteur
     * @throws Refus si la personne est inconnue ou le registre indisponible
     */
    public JsonNode fiche(String identifiant) {
        Lecture lecture = raccordement.fiche();
        byte[] corps = lire(lecture, MediaType.APPLICATION_JSON, tailleFicheMax, identifiant, null);
        JsonNode reponse;
        try {
            reponse = JSON.readTree(corps);
        } catch (JacksonException e) {
            // Le message de l'analyseur peut citer la fiche : il n'est ni propagé ni journalisé.
            throw new Refus(Motif.REGISTRE_INDISPONIBLE);
        }
        JsonNode personne =
                lecture.racine() == null || lecture.racine().isEmpty()
                        ? reponse
                        : Correspondance.lire(reponse, lecture.racine());
        if (personne == null) {
            // Certains registres répondent 200 avec une enveloppe vide pour une personne inconnue.
            throw new Refus(Motif.PERSONNE_INCONNUE);
        }
        if (!personne.isObject()) {
            throw new Refus(Motif.REGISTRE_INDISPONIBLE);
        }
        return raccordement.correspondance() == null
                ? personne
                : Correspondance.traduire(personne, raccordement.correspondance());
    }

    /**
     * @return le document PDF tel que le registre le délivre
     * @throws Refus si la personne ou le document est inconnu, ou le registre indisponible (ou s'il
     *     ne délivre aucun document)
     */
    public byte[] document(String identifiant, String type) {
        if (raccordement.document() == null) {
            throw new Refus(Motif.REGISTRE_INDISPONIBLE);
        }
        return lire(
                raccordement.document(),
                MediaType.APPLICATION_PDF,
                tailleDocumentMax,
                identifiant,
                type);
    }

    private byte[] lire(
            Lecture lecture, MediaType format, int tailleMax, String identifiant, String type) {
        Map<String, String> variables =
                type == null
                        ? Map.of("identifiant", identifiant)
                        : Map.of("identifiant", identifiant, "type", type);
        try {
            RestClient.RequestBodySpec requete =
                    client.method(HttpMethod.valueOf(lecture.methode().name()))
                            .uri(lecture.chemin(), variables)
                            .accept(format);
            if (lecture.methode() == Methode.POST && lecture.corps() != null) {
                // L'identifiant est déjà validé (lettres, chiffres, tirets) : il ne peut pas
                // sortir de la chaîne JSON où il est placé.
                requete.contentType(MediaType.APPLICATION_JSON)
                        .body(lecture.corps().replace("{identifiant}", identifiant));
            }
            return requete.exchange((envoi, reponse) -> {
                if (reponse.getStatusCode().value() == 404) {
                    throw new Refus(Motif.PERSONNE_INCONNUE);
                }
                if (!reponse.getStatusCode().is2xxSuccessful()
                        || !format.isCompatibleWith(reponse.getHeaders().getContentType())) {
                    throw new Refus(Motif.REGISTRE_INDISPONIBLE);
                }
                byte[] corps = reponse.getBody().readNBytes(tailleMax + 1);
                if (corps.length > tailleMax) {
                    throw new Refus(Motif.REGISTRE_INDISPONIBLE);
                }
                return corps;
            });
        } catch (RestClientException | IllegalArgumentException e) {
            throw new Refus(Motif.REGISTRE_INDISPONIBLE);
        }
    }

    /** Le raccordement décrit, ou l'API interne du registre de démonstration ; contrôlé ici. */
    static Raccordement raccordement(ProprietesConnecteur.Registre registre) {
        if ((registre.url() == null) == (registre.raccordement() == null)) {
            throw new IllegalStateException(
                    "sira.connecteur.registre : « url » ou « raccordement », l'un des deux");
        }
        Raccordement raccordement =
                registre.raccordement() == null
                        ? Raccordement.interne(registre.url())
                        : Raccordement.charger(registre.raccordement());
        if (!"https".equals(raccordement.uri().getScheme()) && !registre.httpAdmis()) {
            throw new IllegalStateException(
                    "registre joint en http : https est exigé hors démonstration"
                            + " (sira.connecteur.registre.http-admis)");
        }
        if (registre.raccordement() != null) {
            GardeAdresses.verifier(raccordement.uri(), registre.hotesPermis());
        }
        return raccordement;
    }

    private static SslBundle tls(Authentification authentification) {
        PemSslStoreDetails cles =
                authentification.type() == Raccordement.TypeAuthentification.MTLS
                        ? PemSslStoreDetails.forCertificate(fichier(authentification.certificat()))
                                .withPrivateKey(fichier(authentification.cle()))
                        : null;
        PemSslStoreDetails confiance =
                authentification.autorite() == null
                        ? null
                        : PemSslStoreDetails.forCertificates(fichier(authentification.autorite()));
        return cles == null && confiance == null
                ? null
                : SslBundle.of(new PemSslStoreBundle(cles, confiance));
    }

    private static String fichier(Path chemin) {
        if (!Files.isReadable(chemin)) {
            throw new IllegalStateException("fichier du raccordement illisible : " + chemin);
        }
        return "file:" + chemin.toAbsolutePath();
    }

    /** Un secret monté : une seule ligne, jamais journalisée. */
    static String secret(Path chemin) {
        try {
            String secret = Files.readString(chemin, StandardCharsets.UTF_8).strip();
            if (secret.isEmpty()
                    || secret.length() > TAILLE_SECRET_MAX
                    || !secret.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) {
                throw new IllegalStateException(
                        "secret du raccordement invalide (une ligne ASCII visible) : " + chemin);
            }
            return secret;
        } catch (IOException e) {
            throw new IllegalStateException("secret du raccordement illisible : " + chemin);
        }
    }
}
