package bf.sira.demo.registre.contrat;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.registre.configuration.ProprietesRegistre;
import bf.sira.demo.registre.personnes.DepotPersonnes;
import bf.sira.demo.registre.personnes.Personne;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le contrat commun des registres ({@code registres/openapi.yaml}) : lecture d'une personne par son
 * identifiant unique, sans authentification, comme l'API interne. Chaque registre ne sert que sa
 * propre ressource ; celle d'un autre registre rend 404, comme une personne inconnue.
 */
@RestController
class ContratControleur {

    private final DepotPersonnes personnes;
    private final String registre;

    ContratControleur(DepotPersonnes personnes, ProprietesRegistre proprietes) {
        this.personnes = personnes;
        this.registre = proprietes.code();
    }

    @GetMapping("/v1/etat-civil/personnes/{identifiant}")
    Map<String, Object> etatCivil(@PathVariable String identifiant) {
        return lire("etat-civil", identifiant);
    }

    @GetMapping("/v1/nationalite/personnes/{identifiant}")
    Map<String, Object> nationalite(@PathVariable String identifiant) {
        return lire("nationalite", identifiant);
    }

    @GetMapping("/v1/casier/personnes/{identifiant}")
    Map<String, Object> casier(@PathVariable String identifiant) {
        return lire("casier", identifiant);
    }

    /** Ne lit aucune donnée : dit seulement que le registre répond. */
    @GetMapping("/v1/sante")
    Map<String, String> sante() {
        return Map.of("statut", "OK");
    }

    private Map<String, Object> lire(String ressource, String identifiant) {
        if (!registre.equals(ressource)) {
            throw ErreurMetier.introuvable();
        }
        return personnes
                .lire(identifiant)
                .map(Personne::fiche)
                .flatMap(fiche -> ContratCommun.projeter(registre, fiche))
                .orElseThrow(ErreurMetier::introuvable);
    }
}
