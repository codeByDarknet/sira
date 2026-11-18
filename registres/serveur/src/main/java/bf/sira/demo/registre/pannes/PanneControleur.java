package bf.sira.demo.registre.pannes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Back-office : le panneau qui met le registre en panne, pour montrer que Sira tient. */
@RestController
@RequestMapping("/api/panne")
class PanneControleur {

    static final int DELAI_MIN_MS = 100;
    static final int DELAI_MAX_MS = 30_000;

    record Changement(
            @NotNull(message = "Choisissez un mode.") Panne.Mode mode,
            @Min(value = DELAI_MIN_MS, message = "Le délai va de 0,1 à 30 secondes.")
                    @Max(value = DELAI_MAX_MS, message = "Le délai va de 0,1 à 30 secondes.")
                    Integer delaiMs) {}

    private final DepotPanne depot;

    PanneControleur(DepotPanne depot) {
        this.depot = depot;
    }

    @GetMapping
    Panne lire() {
        return depot.lire();
    }

    @PutMapping
    Panne changer(@Valid @RequestBody Changement changement) {
        depot.changer(changement.mode(), changement.delaiMs());
        return depot.lire();
    }
}
