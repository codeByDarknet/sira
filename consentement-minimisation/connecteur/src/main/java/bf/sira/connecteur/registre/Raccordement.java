package bf.sira.connecteur.registre;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comment lire un registre : décrit dans un fichier, pas dans le code. Le même connecteur lit ainsi
 * le registre de démonstration, celui d'une administration ou celui d'une autre équipe (voir {@code
 * docs/brancher-un-registre.md}).
 *
 * <pre>
 * adresse: https://registre.exemple.bf
 * fiche: { methode: GET, chemin: /v1/personnes/{identifiant}, racine: /donnees }
 * document: { chemin: /v1/personnes/{identifiant}/documents/{type} }   # facultatif
 * authentification: { type: CLE_API, en_tete: X-Api-Key, fichier: /run/sira/secrets/cle }
 * correspondance: { date_naissance: /naissance/date, nationalite: /nationalites/[*]/code }
 * </pre>
 *
 * @param adresse adresse de base du registre : schéma, hôte et port, sans chemin ni secret
 * @param fiche lecture d'une personne ; {@code {identifiant}} est remplacé
 * @param document lecture d'un document PDF ; absent, aucun document n'est délivré
 * @param authentification ce que le registre exige de l'appelant ; aucune si absent
 * @param correspondance nom du champ dans les questions → où le lire dans la réponse ; absente, la
 *     fiche est prise telle quelle (registre de démonstration, API interne)
 */
public record Raccordement(
        String adresse,
        Lecture fiche,
        Lecture document,
        Authentification authentification,
        Map<String, String> correspondance) {

    // Une clé inconnue fait échouer le chargement, comme pour la politique locale.
    private static final JsonMapper LECTEUR =
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    /** Méthodes admises : une lecture ne modifie rien chez le registre. */
    public enum Methode {
        GET,
        POST
    }

    /**
     * @param methode GET par défaut ; POST pour les registres qui veulent l'identifiant dans le
     *     corps
     * @param chemin chemin et requête, avec {@code {identifiant}} (et {@code {type}} pour un
     *     document)
     * @param corps corps JSON d'un POST, où {@code {identifiant}} est remplacé
     * @param racine pointeur JSON vers la personne dans la réponse ; absente ou nulle, la personne
     *     est inconnue
     */
    public record Lecture(Methode methode, String chemin, String corps, String racine) {

        public Lecture {
            methode = methode == null ? Methode.GET : methode;
            if (chemin == null || !chemin.startsWith("/") || !chemin.contains("{identifiant}")) {
                throw new IllegalArgumentException(
                        "chemin : commence par « / » et contient {identifiant}");
            }
            if (chemin.contains("#") || chemin.contains("//") || chemin.contains("..")) {
                throw new IllegalArgumentException("chemin : ni fragment, ni « // », ni « .. »");
            }
            if (corps != null && methode != Methode.POST) {
                throw new IllegalArgumentException("corps : seulement avec la méthode POST");
            }
            if (racine != null && !racine.isEmpty()) {
                Correspondance.verifier(racine);
            }
        }
    }

    /** Ce que le registre exige de l'appelant. */
    public enum TypeAuthentification {
        AUCUNE,
        /** {@code Authorization: Bearer <jeton>}, le jeton lu dans {@code fichier}. */
        JETON,
        /** Une clé dans l'en-tête {@code en_tete}, lue dans {@code fichier}. */
        CLE_API,
        /** Certificat client : {@code certificat} et {@code cle}, au format PEM. */
        MTLS
    }

    /**
     * Les secrets ne sont jamais écrits ici : seulement le chemin du fichier monté qui les
     * contient.
     *
     * @param autorite certificat PEM de l'autorité qui a signé le certificat du registre, si elle
     *     n'est pas reconnue par défaut
     */
    public record Authentification(
            TypeAuthentification type,
            String enTete,
            Path fichier,
            Path certificat,
            Path cle,
            Path autorite) {

        public Authentification {
            type = type == null ? TypeAuthentification.AUCUNE : type;
            switch (type) {
                case JETON, CLE_API -> {
                    if (fichier == null) {
                        throw new IllegalArgumentException(type + " : « fichier » est obligatoire");
                    }
                }
                case MTLS -> {
                    if (certificat == null || cle == null) {
                        throw new IllegalArgumentException(
                                "MTLS : « certificat » et « cle » sont obligatoires");
                    }
                }
                case AUCUNE -> {}
            }
            if (type == TypeAuthentification.CLE_API) {
                enTete = enTete == null ? "X-Api-Key" : enTete;
                if (!enTete.matches("[A-Za-z0-9-]{1,64}")
                        || enTete.toLowerCase(Locale.ROOT).equals("host")) {
                    throw new IllegalArgumentException("en_tete invalide");
                }
            }
        }
    }

    public Raccordement {
        if (adresse == null || fiche == null) {
            throw new IllegalArgumentException("adresse et fiche sont obligatoires");
        }
        adresse = adresse.endsWith("/") ? adresse.substring(0, adresse.length() - 1) : adresse;
        URI uri = uri(adresse);
        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath() == null || uri.getPath().isEmpty())) {
            throw new IllegalArgumentException(
                    "adresse : schéma, hôte et port seulement (ni chemin, ni secret)");
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            throw new IllegalArgumentException("adresse : http ou https");
        }
        if (document != null && document.racine() != null) {
            throw new IllegalArgumentException("document : pas de racine, c'est un PDF");
        }
        authentification =
                authentification == null
                        ? new Authentification(null, null, null, null, null, null)
                        : authentification;
        if (correspondance != null) {
            if (correspondance.isEmpty()) {
                throw new IllegalArgumentException("correspondance vide : rien ne serait lu");
            }
            correspondance.forEach((nom, expression) -> {
                if (nom == null || !nom.matches("[a-z][a-z0-9_]{0,63}")) {
                    throw new IllegalArgumentException(
                            "correspondance : nom de champ en minuscules et soulignés");
                }
                Correspondance.verifier(expression);
            });
            // L'ordre du fichier est gardé : la fiche traduite est toujours la même, champ pour
            // champ (Map.copyOf mélangerait les clés d'une exécution à l'autre).
            correspondance = Collections.unmodifiableMap(new LinkedHashMap<>(correspondance));
        }
    }

    /** Le registre de démonstration lu par son API interne : la fiche entière, sans traduction. */
    public static Raccordement interne(String url) {
        return new Raccordement(
                url,
                new Lecture(null, "/interne/personnes/{identifiant}", null, null),
                new Lecture(null, "/interne/personnes/{identifiant}/documents/{type}", null, null),
                null,
                null);
    }

    /** Lit le fichier monté. Un fichier absent ou invalide empêche le démarrage. */
    public static Raccordement charger(Path fichier) {
        try (Reader lecteur = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            Object contenu = new Yaml(new SafeConstructor(new LoaderOptions())).load(lecteur);
            if (contenu == null) {
                throw new IllegalArgumentException("fichier vide");
            }
            return LECTEUR.convertValue(contenu, Raccordement.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("raccordement illisible : " + fichier, e);
        }
    }

    public URI uri() {
        return uri(adresse);
    }

    private static URI uri(String adresse) {
        try {
            return new URI(adresse);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("adresse invalide");
        }
    }
}
