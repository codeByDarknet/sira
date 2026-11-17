package bf.sira.espacecitoyen.relais;

import bf.sira.commun.erreurs.ErreurMetier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/**} de l'espace citoyen = {@code /v1/citoyen/**} de la passerelle, sans plus. */
@RestController
class RelaisControleur {

    private static final String PREFIXE_LOCAL = "/api";
    private static final String PREFIXE_PASSERELLE = "/v1/citoyen";

    private final Relais relais;

    RelaisControleur(Relais relais) {
        this.relais = relais;
    }

    @RequestMapping(PREFIXE_LOCAL + "/**")
    ResponseEntity<byte[]> relayer(
            HttpServletRequest requete, @RequestBody(required = false) byte[] corps) {
        HttpMethod methode = HttpMethod.valueOf(requete.getMethod());
        // Chemin brut, non décodé : un caractère encodé ne correspond à aucune route.
        String chemin = requete.getRequestURI().substring(PREFIXE_LOCAL.length());
        if (!RoutesCitoyen.autorise(methode, chemin)) {
            throw ErreurMetier.introuvable();
        }
        return relais.relayer(requete, methode, PREFIXE_PASSERELLE + chemin, corps);
    }
}
