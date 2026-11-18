package bf.sira.passerelle.demandes;

import bf.sira.commun.identite.Membre;
import bf.sira.passerelle.config.Formes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les routes des services (contrat §7.1). Le service est celui du certificat présenté : aucun
 * paramètre ne le nomme.
 */
@RestController
class DemandesControleur {

    private final Demandes demandes;

    DemandesControleur(Demandes demandes) {
        this.demandes = demandes;
    }

    @PostMapping("/v1/demandes")
    @ResponseStatus(HttpStatus.CREATED)
    DemandeCreee creer(
            @AuthenticationPrincipal Membre service,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_.:-]{8,128}")
                    String cle,
            @Valid @RequestBody NouvelleDemande demande) {
        return demandes.creer(service.code(), cle, demande);
    }

    @GetMapping("/v1/demandes/{id}")
    EtatDemande lire(
            @AuthenticationPrincipal Membre service,
            @PathVariable UUID id,
            @RequestParam(name = "code_retour", required = false)
                    @Pattern(regexp = "[A-Za-z0-9_-]{16,128}")
                    String codeRetour) {
        return demandes.lire(service.code(), id, codeRetour);
    }

    @DeleteMapping("/v1/demandes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annuler(@AuthenticationPrincipal Membre service, @PathVariable UUID id) {
        demandes.annuler(service.code(), id);
    }

    @GetMapping("/v1/demarches/{code}")
    DemarcheDecrite decrire(
            @AuthenticationPrincipal Membre service,
            @PathVariable @Pattern(regexp = Formes.CODE) String code) {
        return demandes.decrire(service.code(), code);
    }
}
