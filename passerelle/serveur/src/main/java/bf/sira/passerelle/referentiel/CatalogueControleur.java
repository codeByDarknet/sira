package bf.sira.passerelle.referentiel;

import bf.sira.commun.jetons.Acteur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Les routes communes aux catalogues du référentiel (API, « Console »). Les rôles sont contrôlés en
 * amont, par la chaîne de sécurité : lecture pour tous les rôles, écriture pour l'instructeur.
 */
abstract class CatalogueControleur<T> {

    private final Catalogue<T> catalogue;

    CatalogueControleur(Catalogue<T> catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/{code}")
    T lire(@PathVariable @Size(max = 128) String code) {
        return catalogue.lire(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    T creer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody T objet) {
        return catalogue.creer(objet, Acteur.de(jeton));
    }

    @PutMapping("/{code}")
    T modifier(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable @Size(max = 128) String code,
            @Valid @RequestBody T objet) {
        return catalogue.modifier(code, objet, Acteur.de(jeton));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desactiver(
            @AuthenticationPrincipal Jwt jeton, @PathVariable @Size(max = 128) String code) {
        catalogue.desactiver(code, Acteur.de(jeton));
    }
}
