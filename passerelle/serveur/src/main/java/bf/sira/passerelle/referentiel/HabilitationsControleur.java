package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.jetons.Acteur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les habilitations. La chaîne de sécurité réserve l'approbation, le rejet et la suspension à
 * l'autorité, le reste à l'instructeur ; la séparation des personnes est contrôlée ici.
 */
@RestController
@RequestMapping("/v1/admin/habilitations")
class HabilitationsControleur {

    private final Habilitations habilitations;

    HabilitationsControleur(Habilitations habilitations) {
        this.habilitations = habilitations;
    }

    record Motif(@NotBlank @Size(max = 1000) String motif) {}

    @GetMapping
    Page<Habilitation> lister(
            @RequestParam(required = false)
                    @Pattern(regexp = "|BROUILLON|SOUMISE|APPROUVEE|REJETEE|SUSPENDUE")
                    String etat,
            @Valid Pagination pagination,
            @Valid Recherche recherche) {
        return habilitations.lister(etat, pagination, recherche);
    }

    @GetMapping("/{id}")
    Habilitation lire(@PathVariable UUID id) {
        return habilitations.lire(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Habilitation creer(
            @AuthenticationPrincipal Jwt jeton, @Valid @RequestBody SaisieHabilitation saisie) {
        return habilitations.creer(saisie, Acteur.de(jeton));
    }

    @PutMapping("/{id}")
    Habilitation modifier(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody SaisieHabilitation saisie) {
        return habilitations.modifier(id, saisie, Acteur.de(jeton));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        habilitations.retirer(id, Acteur.de(jeton));
    }

    @PostMapping("/{id}/soumission")
    Habilitation soumettre(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        return habilitations.soumettre(id, Acteur.de(jeton));
    }

    @PostMapping("/{id}/approbation")
    Habilitation approuver(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        return habilitations.approuver(id, Acteur.de(jeton));
    }

    @PostMapping("/{id}/rejet")
    Habilitation rejeter(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody Motif motif) {
        return habilitations.rejeter(id, motif.motif(), Acteur.de(jeton));
    }

    @PostMapping("/{id}/suspension")
    Habilitation suspendre(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody Motif motif) {
        return habilitations.suspendre(id, motif.motif(), Acteur.de(jeton));
    }
}
