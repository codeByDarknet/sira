package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.jetons.Acteur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/registres")
class RegistresControleur extends CatalogueControleur<Registre> {

    private final Registres registres;

    RegistresControleur(Registres registres) {
        super(registres);
        this.registres = registres;
    }

    @GetMapping
    Page<Registre> lister(@Valid Pagination pagination, @Valid Recherche recherche) {
        return registres.lister(pagination, recherche);
    }

    /** Réservé à l'autorité (chaîne de sécurité). */
    @PutMapping("/{code}/adresse")
    Registre changerAdresse(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable @Size(max = 128) String code,
            @Valid @RequestBody Adresse adresse) {
        return registres.changerAdresse(code, adresse.adresse(), Acteur.de(jeton));
    }

    record Adresse(@NotNull @Pattern(regexp = Registre.ADRESSE) String adresse) {}
}
