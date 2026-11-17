package bf.sira.demo.registre.personnes;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.registre.Page;
import bf.sira.demo.registre.documents.Documents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Back-office : les personnes du registre, pour les agents de cette administration. */
@RestController
@RequestMapping("/api/personnes")
class PersonnesControleur {

    record Resume(
            UUID id,
            String identifiant,
            Object nom,
            Object prenoms,
            Object dateNaissance,
            Instant modifieeLe) {}

    record Correction(
            @NotEmpty(message = "Aucun champ à modifier.")
                    @Size(max = 20, message = "Trop de champs à la fois.")
                    Map<String, Object> champs) {}

    /** Nouvelle fiche : l'identifiant est facultatif, le registre en attribue un sinon. */
    record Creation(
            @Pattern(regexp = "[0-9]{12}", message = "Saisissez douze chiffres.")
                    String identifiant,
            @NotEmpty(message = "Aucun champ saisi.") @Size(max = 40, message = "Trop de champs.")
                    Map<String, Object> champs) {}

    private static final SecureRandom HASARD = new SecureRandom();

    private final DepotPersonnes depot;
    private final Fiches fiches;
    private final Documents documents;

    PersonnesControleur(DepotPersonnes depot, Fiches fiches, Documents documents) {
        this.depot = depot;
        this.fiches = fiches;
        this.documents = documents;
    }

    @GetMapping
    Page<Resume> lister(
            @RequestParam(defaultValue = "1") @Min(1) @Max(Page.PAGE_MAX) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(Page.TAILLE_MAX) int taille,
            @RequestParam(required = false) @Size(max = 80) String recherche) {
        Page<Personne> personnes = depot.lister(recherche, page, taille);
        return new Page<>(
                personnes.elements().stream().map(PersonnesControleur::resume).toList(),
                page,
                taille,
                personnes.total());
    }

    @GetMapping("/{id}")
    Personne lire(@PathVariable UUID id) {
        return depot.lireParId(id).orElseThrow(ErreurMetier::introuvable);
    }

    @PatchMapping("/{id}")
    @Transactional
    Personne corriger(@PathVariable UUID id, @Valid @RequestBody Correction correction) {
        return depot.corriger(lire(id).identifiant(), fiches.controler(correction.champs()))
                .orElseThrow(ErreurMetier::introuvable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Personne creer(@Valid @RequestBody Creation creation) {
        Map<String, Object> fiche = new LinkedHashMap<>();
        String identifiant = creation.identifiant();
        if (identifiant == null) {
            do {
                identifiant = "99" + String.format("%010d", HASARD.nextLong(10_000_000_000L));
            } while (depot.lire(identifiant).isPresent());
        }
        fiche.put(Personne.IDENTIFIANT, identifiant);
        fiche.putAll(fiches.controlerCreation(creation.champs()));
        if (!depot.ajouter(identifiant, fiche)) {
            throw ErreurMetier.conflit("Une personne porte déjà cet identifiant.");
        }
        return depot.lire(identifiant).orElseThrow(ErreurMetier::introuvable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable UUID id) {
        if (!depot.supprimer(id)) {
            throw ErreurMetier.introuvable();
        }
    }

    @GetMapping("/{id}/documents/{type}")
    ResponseEntity<byte[]> document(@PathVariable UUID id, @PathVariable String type) {
        return documents.reponse(lire(id).identifiant(), type);
    }

    private static Resume resume(Personne personne) {
        Map<String, Object> fiche = personne.fiche();
        return new Resume(
                personne.id(),
                personne.identifiant(),
                fiche.get(Personne.NOM),
                fiche.get(Personne.PRENOMS),
                fiche.get(Personne.DATE_NAISSANCE),
                personne.modifieeLe());
    }
}
