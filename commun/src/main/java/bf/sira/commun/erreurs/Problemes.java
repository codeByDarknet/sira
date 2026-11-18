package bf.sira.commun.erreurs;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Fabrique des réponses d'erreur {@code application/problem+json} (RFC 9457). */
public final class Problemes {

    private static final String PREFIXE_TYPE = "urn:sira:erreur:";

    private record Libelle(String code, String titre) {}

    private static final Map<Integer, Libelle> LIBELLES =
            Map.ofEntries(
                    Map.entry(400, new Libelle("requete-invalide", "Requête invalide")),
                    Map.entry(401, new Libelle("non-authentifie", "Authentification requise")),
                    Map.entry(403, new Libelle("acces-refuse", "Accès refusé")),
                    Map.entry(404, new Libelle("introuvable", "Ressource introuvable")),
                    Map.entry(405, new Libelle("methode-refusee", "Méthode non autorisée")),
                    Map.entry(406, new Libelle("format-refuse", "Format de réponse indisponible")),
                    Map.entry(409, new Libelle("conflit", "Conflit avec l'état actuel")),
                    Map.entry(413, new Libelle("corps-trop-volumineux", "Corps trop volumineux")),
                    Map.entry(
                            415,
                            new Libelle("format-non-pris-en-charge", "Format non pris en charge")),
                    Map.entry(422, new Libelle("contenu-refuse", "Contenu refusé")),
                    Map.entry(429, new Libelle("trop-de-requetes", "Trop de requêtes")),
                    Map.entry(
                            503,
                            new Libelle("indisponible", "Service momentanément indisponible")));

    private static final Libelle ERREUR_CLIENT =
            new Libelle("requete-invalide", "Requête invalide");
    private static final Libelle ERREUR_SERVEUR = new Libelle("erreur-interne", "Erreur interne");

    private Problemes() {}

    /**
     * Problème générique pour un statut : seul le libellé convenu sort, jamais un message
     * technique.
     */
    public static ProblemDetail pour(HttpStatusCode statut) {
        Libelle libelle =
                LIBELLES.getOrDefault(
                        statut.value(), statut.is4xxClientError() ? ERREUR_CLIENT : ERREUR_SERVEUR);
        return creer(statut, libelle.code(), libelle.titre(), null);
    }

    public static ProblemDetail pour(ErreurMetier erreur) {
        return creer(erreur.statut(), erreur.code(), erreur.titre(), erreur.detail());
    }

    private static ProblemDetail creer(
            HttpStatusCode statut, String code, String titre, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setType(URI.create(PREFIXE_TYPE + code));
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        return probleme;
    }

    /** Réponse 401 des API : jamais de redirection ni de page HTML. */
    public static AuthenticationEntryPoint nonAuthentifie() {
        return (requete, reponse, exception) -> ecrire(reponse, HttpStatus.UNAUTHORIZED);
    }

    /** Réponse 403 des API. */
    public static AccessDeniedHandler accesRefuse() {
        return (requete, reponse, exception) -> ecrire(reponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Écrit un problème générique hors de Spring MVC (filtres de sécurité). Les libellés sont des
     * constantes sans caractère à échapper : le JSON est composé directement.
     */
    public static void ecrire(HttpServletResponse reponse, HttpStatus statut) throws IOException {
        ProblemDetail probleme = pour(statut);
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter()
                .write(
                        "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d}"
                                .formatted(
                                        probleme.getType(), probleme.getTitle(), statut.value()));
    }
}
