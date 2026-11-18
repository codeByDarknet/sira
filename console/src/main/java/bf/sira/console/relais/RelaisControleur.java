package bf.sira.console.relais;

import bf.sira.commun.erreurs.ErreurMetier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/**} de la console = {@code /v1/admin/**} de la passerelle, sans plus. */
@RestController
class RelaisControleur {

    private static final String PREFIXE_LOCAL = "/api";
    private static final String PREFIXE_PASSERELLE = "/v1/admin";

    private final Relais relais;

    RelaisControleur(Relais relais) {
        this.relais = relais;
    }

    @RequestMapping(PREFIXE_LOCAL + "/**")
    ResponseEntity<byte[]> relayer(
            HttpServletRequest requete,
            Authentication administrateur,
            @RequestBody(required = false) byte[] corps) {
        HttpMethod methode = HttpMethod.valueOf(requete.getMethod());
        // Chemin brut, non décodé : un caractère encodé ne correspond à aucune route.
        String chemin = requete.getRequestURI().substring(PREFIXE_LOCAL.length());
        Route route = RoutesConsole.trouver(methode, chemin).orElseThrow(ErreurMetier::introuvable);
        if (!route.acces().accordeA(administrateur.getAuthorities())) {
            throw new ErreurMetier(HttpStatus.FORBIDDEN, "acces-refuse", "Accès refusé");
        }
        return relais.relayer(requete, methode, PREFIXE_PASSERELLE + chemin, corps);
    }
}
