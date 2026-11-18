package bf.sira.commun.agents;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Les comptes des agents, dans l'annuaire Keycloak de l'administration. L'application s'y présente
 * avec son propre certificat (compte de service du client, rôles {@code manage-users} et {@code
 * view-users}) et ne touche qu'aux comptes dont l'attribut {@code administration} est le sien : un
 * compte d'une autre administration est traité comme introuvable.
 */
public class AnnuaireKeycloak {

    private static final Logger JOURNAL = LoggerFactory.getLogger(AnnuaireKeycloak.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> OBJET =
            new ParameterizedTypeReference<>() {};

    /** Au-delà, l'annuaire d'une administration n'est pas lu d'un coup (démonstration). */
    private static final int AGENTS_MAX = 500;

    /** Un agent tel que les écrans le montrent. */
    public record Compte(
            String id,
            String identifiant,
            String prenom,
            String nom,
            String email,
            boolean actif,
            boolean premiereConnexion,
            Instant creeLe) {}

    private final RestClient client;
    private final String pointDeJetons;
    private final String clientId;
    private final String administration;
    private volatile String jeton;
    private volatile Instant expiration = Instant.EPOCH;

    /**
     * @param client client HTTP en mTLS vers le fournisseur d'identité
     * @param urlRoyaume adresse interne du royaume : {@code https://keycloak:8443/realms/x}
     */
    public AnnuaireKeycloak(
            RestClient client,
            String urlRoyaume,
            String pointDeJetons,
            String clientId,
            String administration) {
        this.client =
                client.mutate().baseUrl(urlRoyaume.replace("/realms/", "/admin/realms/")).build();
        this.pointDeJetons = pointDeJetons;
        this.clientId = clientId;
        this.administration = administration;
    }

    /** Les agents de l'administration, triés par nom. */
    public List<Compte> lister() {
        List<Map<String, Object>> comptes =
                appeler(() ->
                        client.get()
                                .uri(u ->
                                        u.path("/users")
                                                .queryParam("q", "administration:" + administration)
                                                .queryParam("briefRepresentation", false)
                                                .queryParam("max", AGENTS_MAX)
                                                .build())
                                .headers(h -> h.setBearerAuth(jeton()))
                                .retrieve()
                                .body(LISTE));
        return comptes == null
                ? List.of()
                : comptes.stream()
                        .filter(this::estDeLAdministration)
                        .map(AnnuaireKeycloak::compte)
                        .sorted((a, b) ->
                                (a.nom() + a.prenom()).compareToIgnoreCase(b.nom() + b.prenom()))
                        .toList();
    }

    public Compte lire(String id) {
        return compte(brut(id));
    }

    /**
     * Crée le compte : identifiant = adresse électronique, mot de passe provisoire, et à la
     * première connexion, changement du mot de passe puis enregistrement de l'application
     * d'authentification.
     *
     * @return l'identifiant technique du compte créé
     */
    public String creer(String prenom, String nom, String email, String motDePasse) {
        Map<String, Object> compte = new LinkedHashMap<>();
        compte.put("username", email);
        compte.put("email", email);
        compte.put("firstName", prenom);
        compte.put("lastName", nom);
        compte.put("enabled", true);
        compte.put("emailVerified", true);
        compte.put("attributes", Map.of("administration", List.of(administration)));
        compte.put("requiredActions", List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"));
        compte.put(
                "credentials",
                List.of(Map.of("type", "password", "value", motDePasse, "temporary", true)));
        URI adresse =
                appeler(() ->
                        client.post()
                                .uri("/users")
                                .headers(h -> h.setBearerAuth(jeton()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(compte)
                                .retrieve()
                                .toBodilessEntity()
                                .getHeaders()
                                .getLocation());
        if (adresse == null) {
            throw new IllegalStateException("l'annuaire n'a pas donné l'adresse du compte créé");
        }
        String chemin = adresse.getPath();
        return chemin.substring(chemin.lastIndexOf('/') + 1);
    }

    /** Change nom, prénom, adresse et état ; l'identifiant de connexion ne change jamais. */
    public Compte modifier(String id, String prenom, String nom, String email, boolean actif) {
        Map<String, Object> compte = new LinkedHashMap<>(brut(id));
        compte.put("firstName", prenom);
        compte.put("lastName", nom);
        compte.put("email", email);
        compte.put("enabled", actif);
        appeler(() ->
                client.put()
                        .uri("/users/{id}", id)
                        .headers(h -> h.setBearerAuth(jeton()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(compte)
                        .retrieve()
                        .toBodilessEntity());
        return lire(id);
    }

    /** Nouveau mot de passe provisoire, à changer à la prochaine connexion. */
    public void reinitialiser(String id, String motDePasse) {
        Map<String, Object> compte = new LinkedHashMap<>(brut(id));
        List<Object> actions = new ArrayList<>();
        if (compte.get("requiredActions") instanceof List<?> existantes) {
            actions.addAll(existantes);
        }
        if (!actions.contains("UPDATE_PASSWORD")) {
            actions.add(0, "UPDATE_PASSWORD");
        }
        compte.put("requiredActions", actions);
        appeler(() ->
                client.put()
                        .uri("/users/{id}/reset-password", id)
                        .headers(h -> h.setBearerAuth(jeton()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("type", "password", "value", motDePasse, "temporary", true))
                        .retrieve()
                        .toBodilessEntity());
        appeler(() ->
                client.put()
                        .uri("/users/{id}", id)
                        .headers(h -> h.setBearerAuth(jeton()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(compte)
                        .retrieve()
                        .toBodilessEntity());
    }

    public void supprimer(String id) {
        brut(id);
        appeler(() ->
                client.delete()
                        .uri("/users/{id}", id)
                        .headers(h -> h.setBearerAuth(jeton()))
                        .retrieve()
                        .toBodilessEntity());
    }

    /** Le compte tel que l'annuaire le rend, s'il appartient à cette administration. */
    private Map<String, Object> brut(String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) {
            throw ErreurMetier.introuvable();
        }
        Map<String, Object> compte;
        try {
            compte =
                    appeler(() ->
                            client.get()
                                    .uri("/users/{id}", id)
                                    .headers(h -> h.setBearerAuth(jeton()))
                                    .retrieve()
                                    .body(OBJET));
        } catch (ErreurMetier e) {
            throw ErreurMetier.introuvable();
        }
        if (compte == null || !estDeLAdministration(compte)) {
            throw ErreurMetier.introuvable();
        }
        return compte;
    }

    private boolean estDeLAdministration(Map<String, Object> compte) {
        return compte.get("attributes") instanceof Map<?, ?> attributs
                && attributs.get("administration") instanceof List<?> valeurs
                && valeurs.contains(administration);
    }

    private static Compte compte(Map<String, Object> brut) {
        Object cree = brut.get("createdTimestamp");
        return new Compte(
                String.valueOf(brut.get("id")),
                String.valueOf(brut.get("username")),
                texte(brut.get("firstName")),
                texte(brut.get("lastName")),
                texte(brut.get("email")),
                Boolean.TRUE.equals(brut.get("enabled")),
                brut.get("requiredActions") instanceof List<?> actions && !actions.isEmpty(),
                cree instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : null);
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : String.valueOf(valeur);
    }

    /** Jeton du compte de service, demandé en mTLS et gardé jusqu'à peu avant son échéance. */
    private String jeton() {
        if (jeton != null && Instant.now().isBefore(expiration)) {
            return jeton;
        }
        synchronized (this) {
            if (jeton == null || !Instant.now().isBefore(expiration)) {
                LinkedMultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
                formulaire.add("grant_type", "client_credentials");
                formulaire.add("client_id", clientId);
                Map<String, Object> reponse =
                        client.post()
                                .uri(pointDeJetons)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(formulaire)
                                .retrieve()
                                .body(OBJET);
                if (reponse == null || !(reponse.get("access_token") instanceof String valeur)) {
                    throw new IllegalStateException("aucun jeton reçu de l'annuaire");
                }
                long duree = reponse.get("expires_in") instanceof Number n ? n.longValue() : 60;
                jeton = valeur;
                expiration = Instant.now().plusSeconds(Math.max(10, duree - 20));
            }
            return jeton;
        }
    }

    /** Traduit les refus de l'annuaire en erreurs lisibles ; le détail reste dans le journal. */
    private <T> T appeler(java.util.function.Supplier<T> appel) {
        try {
            return appel.get();
        } catch (HttpClientErrorException.Conflict e) {
            throw new SaisieRefusee("email", "Un compte existe déjà avec cette adresse.");
        } catch (HttpClientErrorException.NotFound e) {
            throw ErreurMetier.introuvable();
        } catch (HttpClientErrorException e) {
            JOURNAL.warn("annuaire : refus {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 400) {
                throw new SaisieRefusee("email", "L’annuaire refuse ces informations.");
            }
            throw indisponible();
        } catch (RestClientException e) {
            JOURNAL.warn("annuaire injoignable : {}", e.getMessage());
            throw indisponible();
        }
    }

    private static ErreurMetier indisponible() {
        return new ErreurMetier(
                HttpStatus.SERVICE_UNAVAILABLE,
                "annuaire-indisponible",
                "L’annuaire des agents ne répond pas",
                "Réessayez dans un instant.");
    }
}
