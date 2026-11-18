package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/demarches")
class DemarchesControleur extends CatalogueControleur<Demarche> {

    private final Demarches demarches;

    DemarchesControleur(Demarches demarches) {
        super(demarches);
        this.demarches = demarches;
    }

    @GetMapping
    Page<Demarche> lister(@Valid Pagination pagination, @Valid Recherche recherche) {
        return demarches.lister(pagination, recherche);
    }
}
