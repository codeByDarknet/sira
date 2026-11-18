package bf.sira.demo.service.agent;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.service.Page;
import bf.sira.demo.service.demandes.DemandeSuivie;
import bf.sira.demo.service.demandes.DepotDemandes;
import bf.sira.demo.service.demandes.Origine;
import bf.sira.demo.service.demandes.SuiviDemandes;
import bf.sira.demo.service.dossiers.DepotDossiers;
import bf.sira.demo.service.dossiers.Dossier;
import bf.sira.demo.service.dossiers.Dossiers;
import bf.sira.demo.service.dossiers.EtatDossier;
import bf.sira.demo.service.dossiers.VueDossier;
import bf.sira.kit.Canal;
import bf.sira.kit.DemandeCreee;
import bf.sira.kit.EtatDemandeCode;
import bf.sira.kit.NouvelleDemande;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le back-office des agents du service : les dossiers reçus, et rien d'autre que ce que les
 * registres ont attesté. L'agent ne consulte jamais un registre lui-même : s'il lui manque une
 * vérification, il la redemande au citoyen, qui l'accepte ou non depuis son espace Sira.
 */
@RestController
@RequestMapping("/api/agent/dossiers")
class AgentControleur {

    private static final String CODE = "[a-z0-9.-]{1,80}";

    record Ligne(UUID id, String numero, EtatDossier etat, Instant creeLe, Instant modifieLe) {}

    record Demande(UUID id, Canal canal, Origine origine, EtatDemandeCode etat, Instant creeeLe) {}

    record Detail(VueDossier dossier, List<Demande> demandes) {}

    private final DepotDossiers depot;
    private final Dossiers dossiers;
    private final DepotDemandes demandes;
    private final SuiviDemandes suivi;
    private final Clock horloge;

    AgentControleur(
            DepotDossiers depot,
            Dossiers dossiers,
            DepotDemandes demandes,
            SuiviDemandes suivi,
            Clock horloge) {
        this.depot = depot;
        this.dossiers = dossiers;
        this.demandes = demandes;
        this.suivi = suivi;
        this.horloge = horloge;
    }

    @GetMapping
    Page<Ligne> lister(
            @RequestParam(defaultValue = "1") @Min(1) @Max(Page.PAGE_MAX) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(Page.TAILLE_MAX) int taille,
            @RequestParam(required = false) EtatDossier etat) {
        Page<Dossier> trouves = depot.lister(etat, page, taille);
        return new Page<>(
                trouves.elements().stream()
                        .map(d ->
                                new Ligne(d.id(), d.numero(), d.etat(), d.creeLe(), d.modifieLe()))
                        .toList(),
                page,
                taille,
                trouves.total());
    }

    @GetMapping("/{id}")
    Detail lire(@PathVariable UUID id) {
        return new Detail(
                dossiers.vue(id),
                demandes.duDossier(id).stream()
                        .map(d ->
                                new Demande(d.id(), d.canal(), d.origine(), d.etat(), d.creeeLe()))
                        .toList());
    }

    /** Pièce reçue d'un registre ; {@code apercu=true} affiche un PDF dans le navigateur. */
    @GetMapping("/{id}/pieces/{jeu}")
    ResponseEntity<byte[]> piece(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = CODE) String jeu,
            @RequestParam(defaultValue = "false") boolean apercu) {
        return dossiers.piece(id, jeu, apercu);
    }

    /** Pièce jointe par la personne elle-même : déclarée, jamais vérifiée par un registre. */
    @GetMapping("/{id}/declarations/{code}/piece")
    ResponseEntity<byte[]> pieceDeclaree(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = CODE) String code,
            @RequestParam(defaultValue = "false") boolean apercu) {
        return dossiers.pieceDeclaree(id, code, apercu);
    }

    /**
     * Redemande une vérification : la demande part vers l'espace du citoyen, désigné par son
     * pseudonyme. Tant qu'il n'a pas répondu à la précédente, on ne lui en envoie pas une autre.
     */
    @PostMapping("/{id}/verifications")
    @ResponseStatus(HttpStatus.CREATED)
    Demande redemander(@PathVariable UUID id) {
        Dossier dossier = depot.lire(id).orElseThrow(ErreurMetier::introuvable);
        if (demandes.duDossier(id).stream().anyMatch(this::enAttenteDuCitoyen)) {
            throw new ErreurMetier(
                    HttpStatus.CONFLICT,
                    "demande-deja-envoyee",
                    "Demande déjà envoyée",
                    "Une demande attend déjà la réponse du citoyen.");
        }
        DemandeCreee creee =
                suivi.ouvrir(
                        demarche -> NouvelleDemande.parPseudonyme(demarche, dossier.pseudonyme()),
                        Origine.AGENT,
                        id);
        return new Demande(
                creee.id(), Canal.NOTIFICATION, Origine.AGENT, creee.etat(), horloge.instant());
    }

    private boolean enAttenteDuCitoyen(DemandeSuivie demande) {
        return demande.etat() == EtatDemandeCode.EN_ATTENTE_ACCORD
                && demande.expireLe().isAfter(horloge.instant());
    }
}
