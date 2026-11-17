package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.jetons.Acteur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/membres")
class MembresControleur extends CatalogueControleur<MembreDeclare> {

    private final Membres membres;

    MembresControleur(Membres membres) {
        super(membres);
        this.membres = membres;
    }

    @GetMapping
    Page<MembreDeclare> lister(@Valid Pagination pagination, @Valid Recherche recherche) {
        return membres.lister(pagination, recherche);
    }

    /** Réservé à l'autorité (chaîne de sécurité). */
    @PutMapping("/{code}/retours")
    MembreDeclare changerRetours(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable @Size(max = 128) String code,
            @Valid @RequestBody Retours retours) {
        return membres.changerRetours(code, retours.retoursAutorises(), Acteur.de(jeton));
    }

    record Retours(
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> retoursAutorises) {}
}
