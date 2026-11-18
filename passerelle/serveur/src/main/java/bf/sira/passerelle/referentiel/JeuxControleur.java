package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/jeux")
class JeuxControleur extends CatalogueControleur<Jeu> {

    private final Jeux jeux;

    JeuxControleur(Jeux jeux) {
        super(jeux);
        this.jeux = jeux;
    }

    @GetMapping
    Page<Jeu> lister(
            @RequestParam(required = false) @Size(max = 64) String registre,
            @RequestParam(required = false) @Pattern(regexp = "|STATUT|CHAMPS|DOCUMENT")
                    String niveau,
            @Valid Pagination pagination,
            @Valid Recherche recherche) {
        return jeux.lister(registre, niveau, pagination, recherche);
    }
}
