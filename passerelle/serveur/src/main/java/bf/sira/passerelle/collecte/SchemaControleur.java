package bf.sira.passerelle.collecte;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.passerelle.referentiel.Registre;
import bf.sira.passerelle.referentiel.Registres;
import bf.sira.resilience.ConnecteurInjoignable;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le schéma d'un registre, lu à la demande chez son connecteur : les champs (avec leur nature) et
 * les documents que sa politique locale expose. La console ne propose rien d'autre à la création
 * d'une question. Rien n'est gardé ici : le registre reste seul maître de ce qu'il expose.
 */
@RestController
class SchemaControleur {

    private final Registres registres;
    private final Map<String, Transport> transports;

    SchemaControleur(Registres registres, List<Transport> transports) {
        this.registres = registres;
        this.transports = Transport.parCode(transports);
    }

    @GetMapping(
            path = "/v1/admin/registres/{code}/schema",
            produces = MediaType.APPLICATION_JSON_VALUE)
    String schema(@PathVariable String code) {
        Registre registre = registres.trouver(code).orElseThrow(ErreurMetier::introuvable);
        Transport transport = transports.get(registre.transport());
        if (transport == null) {
            throw indisponible();
        }
        try {
            return transport.schema(registre.code(), registre.adresse());
        } catch (ConnecteurInjoignable e) {
            throw indisponible();
        }
    }

    private static ErreurMetier indisponible() {
        return new ErreurMetier(
                HttpStatus.SERVICE_UNAVAILABLE,
                "registre-indisponible",
                "Registre indisponible",
                "Le registre ne répond pas pour l'instant. Réessayez dans un moment.");
    }
}
