package bf.sira.demo.registre.appels;

import bf.sira.demo.registre.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office : qui a interrogé le registre. En service normal, seul le connecteur de Sira. */
@RestController
class AppelsControleur {

    private final DepotAppels depot;

    AppelsControleur(DepotAppels depot) {
        this.depot = depot;
    }

    @GetMapping("/api/appels")
    Page<Appel> lister(
            @RequestParam(defaultValue = "1") @Min(1) @Max(Page.PAGE_MAX) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(Page.TAILLE_MAX) int taille) {
        return depot.lister(page, taille);
    }
}
