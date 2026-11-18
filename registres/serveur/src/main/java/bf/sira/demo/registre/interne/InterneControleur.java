package bf.sira.demo.registre.interne;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.registre.documents.Documents;
import bf.sira.demo.registre.personnes.DepotPersonnes;
import bf.sira.demo.registre.personnes.Personne;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'API du « système ancien » (contrat §9) : sans authentification, elle rend la fiche entière à
 * qui la demande. C'est précisément ce que le connecteur de Sira, seul à pouvoir la joindre, est là
 * pour garder.
 */
@RestController
@RequestMapping("/interne/personnes/{identifiant}")
class InterneControleur {

    private final DepotPersonnes personnes;
    private final Documents documents;

    InterneControleur(DepotPersonnes personnes, Documents documents) {
        this.personnes = personnes;
        this.documents = documents;
    }

    @GetMapping
    Map<String, Object> fiche(@PathVariable String identifiant) {
        return personnes
                .lire(identifiant)
                .map(Personne::fiche)
                .orElseThrow(ErreurMetier::introuvable);
    }

    @GetMapping("/documents/{type}")
    ResponseEntity<byte[]> document(@PathVariable String identifiant, @PathVariable String type) {
        return documents.reponse(identifiant, type);
    }
}
