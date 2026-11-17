package bf.sira.espacecitoyen.relais;

import bf.sira.commun.config.ProprietesCommun;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.oidc.RelaisPasserelle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Relaie une requête déjà admise vers la passerelle, en mTLS, avec le jeton d'accès de la session
 * (renouvelé au besoin). Rien de ce qu'envoie le navigateur ne part tel quel : ni cookie, ni
 * en-tête ; seuls le corps JSON et les paramètres de requête, bornés, sont repris. La réponse de la
 * passerelle, succès ou erreur, est rendue sans transformation.
 */
@Component
class Relais {

    static final int TAILLE_REPONSE_MAX = 1024 * 1024;

    private static final int PARAMETRES_MAX = 12;
    private static final int TAILLE_VALEUR_MAX = 200;
    private static final Pattern NOM_PARAMETRE = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    private static final Logger journal = LoggerFactory.getLogger(Relais.class);

    private final RestClient passerelle;
    private final String adresse;

    Relais(RelaisPasserelle passerelle, ProprietesCommun proprietes) {
        this.passerelle = passerelle.client();
        this.adresse = proprietes.passerelle().url().replaceAll("/+$", "");
    }

    /**
     * @param chemin chemin complet côté passerelle, déjà validé par la liste blanche
     */
    ResponseEntity<byte[]> relayer(
            HttpServletRequest requete, HttpMethod methode, String chemin, byte[] corps) {
        exigerJson(requete);
        RestClient.RequestBodySpec appel =
                passerelle
                        .method(methode)
                        .uri(URI.create(adresse + chemin + parametres(requete)))
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
        if (corps != null && corps.length > 0) {
            appel.contentType(MediaType.APPLICATION_JSON).body(corps);
        }
        ResponseEntity<byte[]> reponse;
        try {
            reponse = appel.exchange((envoyee, recue) -> lire(recue));
        } catch (ClientAuthorizationException e) {
            // Jeton non renouvelable : la session du fournisseur est finie, celle-ci aussi.
            fermerSession(requete);
            throw new ErreurMetier(
                    HttpStatus.UNAUTHORIZED, "non-authentifie", "Authentification requise");
        } catch (ResourceAccessException e) {
            journal.warn("Passerelle injoignable");
            throw new ErreurMetier(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "indisponible",
                    "Service momentanément indisponible");
        }
        if (reponse.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            // La passerelle ne reconnaît plus le jeton : l'interface doit reconnecter.
            fermerSession(requete);
        }
        return reponse;
    }

    /** Un corps n'est accepté qu'en JSON : la passerelle ne parle rien d'autre. */
    private static void exigerJson(HttpServletRequest requete) {
        String type = requete.getContentType();
        if (type == null) {
            return;
        }
        try {
            if (MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(type))) {
                return;
            }
        } catch (InvalidMediaTypeException e) {
            // Refusé comme tout autre format.
        }
        throw new ErreurMetier(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "format-non-pris-en-charge",
                "Format non pris en charge");
    }

    /** Paramètres de requête réencodés un à un ; nombre, noms et tailles bornés. */
    private static String parametres(HttpServletRequest requete) {
        List<String> paires = new ArrayList<>();
        for (Map.Entry<String, String[]> parametre : requete.getParameterMap().entrySet()) {
            for (String valeur : parametre.getValue()) {
                if (paires.size() == PARAMETRES_MAX
                        || !NOM_PARAMETRE.matcher(parametre.getKey()).matches()
                        || valeur.length() > TAILLE_VALEUR_MAX) {
                    throw new ErreurMetier(
                            HttpStatus.BAD_REQUEST, "requete-invalide", "Requête invalide");
                }
                paires.add(
                        parametre.getKey()
                                + "="
                                + URLEncoder.encode(valeur, StandardCharsets.UTF_8));
            }
        }
        return paires.isEmpty() ? "" : "?" + String.join("&", paires);
    }

    /**
     * La passerelle répond en JSON ou en problème RFC 9457, d'une taille bornée. Toute autre
     * réponse (redirection, page, flux sans fin) trahit une anomalie et n'est pas transmise.
     */
    private static ResponseEntity<byte[]> lire(ClientHttpResponse reponse) throws IOException {
        HttpStatusCode statut = reponse.getStatusCode();
        byte[] contenu = reponse.getBody().readNBytes(TAILLE_REPONSE_MAX + 1);
        MediaType type = reponse.getHeaders().getContentType();
        boolean json =
                type != null
                        && (MediaType.APPLICATION_JSON.equalsTypeAndSubtype(type)
                                || MediaType.APPLICATION_PROBLEM_JSON.equalsTypeAndSubtype(type));
        if (statut.is1xxInformational()
                || statut.is3xxRedirection()
                || contenu.length > TAILLE_REPONSE_MAX
                || (contenu.length > 0 && !json)) {
            journal.warn("Réponse inattendue de la passerelle : statut={}", statut.value());
            throw new ErreurMetier(
                    HttpStatus.BAD_GATEWAY,
                    "reponse-passerelle-invalide",
                    "Réponse inattendue de la passerelle");
        }
        ResponseEntity.BodyBuilder sortie = ResponseEntity.status(statut);
        return contenu.length == 0 ? sortie.build() : sortie.contentType(type).body(contenu);
    }

    private static void fermerSession(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
