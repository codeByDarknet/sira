package bf.sira.demo.service.parcours;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.oidc.ProprietesOidc;
import bf.sira.demo.service.demandes.DemandeSuivie;
import bf.sira.demo.service.demandes.DepotDemandes;
import bf.sira.demo.service.demandes.Origine;
import bf.sira.demo.service.demandes.SuiviDemandes;
import bf.sira.demo.service.dossiers.Declarations;
import bf.sira.demo.service.dossiers.Dossiers;
import bf.sira.demo.service.dossiers.VueDossier;
import bf.sira.demo.service.parcours.SessionParcours.Affichage;
import bf.sira.kit.Canal;
import bf.sira.kit.DemandeCreee;
import bf.sira.kit.EtatDemandeCode;
import bf.sira.kit.NouvelleDemande;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Le parcours du citoyen. Aucune route ne prend d'identifiant de dossier : le navigateur n'atteint
 * que la demande et le dossier de sa propre session, tout le reste répond 404.
 */
@RestController
@RequestMapping("/api/parcours")
class ParcoursControleur {

    private static final String IDENTIFIANT_UNIQUE = "[0-9]{12}";

    private static final String CODE = "[a-z0-9.-]{1,80}";

    /**
     * @param identifiantUnique transmis à Sira pour les trois canaux, qui vérifie qu'il désigne la
     *     personne qui donne son accord ; jamais gardé ni journalisé ici
     */
    record Ouverture(
            @NotNull(message = "Choisissez une façon de continuer.") Canal canal,
            @NotNull(message = "Saisissez votre identifiant unique.")
                    @Pattern(
                            regexp = IDENTIFIANT_UNIQUE,
                            message = "L'identifiant unique compte 12 chiffres.")
                    String identifiantUnique) {

        @Override
        public String toString() {
            return "Ouverture[canal=" + canal + "]";
        }
    }

    /** La longueur est bornée par le service, qui répond par une erreur de saisie. */
    record Saisie(String valeur) {}

    record Retour(
            @NotNull UUID demande,
            @NotNull @Pattern(regexp = "[\\x21-\\x7E]{1,512}") String codeRetour) {}

    /**
     * @param dossierOuvert vrai quand l'accord est donné : le dossier de la session est lisible
     */
    record VueDemande(
            UUID id,
            Canal canal,
            EtatDemandeCode etat,
            String urlAccord,
            String codeCourt,
            String codeVerification,
            Instant expireLe,
            boolean dossierOuvert) {}

    private final SuiviDemandes suivi;
    private final DepotDemandes demandes;
    private final Dossiers dossiers;
    private final Declarations declarations;
    private final LimiteDeDebit limite;
    private final String retourUrl;

    ParcoursControleur(
            SuiviDemandes suivi,
            DepotDemandes demandes,
            Dossiers dossiers,
            Declarations declarations,
            LimiteDeDebit limite,
            ProprietesOidc adresses) {
        this.suivi = suivi;
        this.demandes = demandes;
        this.dossiers = dossiers;
        this.declarations = declarations;
        this.limite = limite;
        this.retourUrl = adresses.urlPublique() + "/retour";
    }

    @PostMapping("/demandes")
    @ResponseStatus(HttpStatus.CREATED)
    VueDemande ouvrir(@Valid @RequestBody Ouverture ouverture, HttpServletRequest requete) {
        Function<String, NouvelleDemande> canal = canal(ouverture);
        HttpSession session = requete.getSession();
        if (!limite.admettre(session.getId())) {
            throw new ErreurMetier(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "trop-de-requetes",
                    "Trop de requêtes",
                    "Trop de demandes en peu de temps. Patientez une minute, puis réessayez.");
        }
        DemandeCreee creee = suivi.ouvrir(canal, Origine.CITOYEN, null);
        Affichage affichage = SessionParcours.retenir(requete, ouverture.canal(), creee);
        return vue(affichage, creee.etat(), false);
    }

    /** L'écran d'attente appelle cette route à intervalle régulier, jusqu'à l'accord. */
    @GetMapping("/demande")
    VueDemande suivre(HttpServletRequest requete) {
        Affichage affichage = affichage(requete);
        DemandeSuivie demande = suivie(affichage);
        // Par redirection, rien n'est remis sans le code de retour : inutile d'interroger Sira.
        if (affichage.canal() != Canal.REDIRECTION || demande.codeRetour() != null) {
            demande = suivi.synchroniser(demande);
        }
        return ouvrirDossier(requete, affichage, demande);
    }

    /**
     * Retour du navigateur après l'écran d'accord. La demande nommée doit être celle de la session
     * : le lien de retour d'un autre navigateur, même authentique, ne donne accès à rien.
     */
    @PostMapping("/retour")
    VueDemande revenir(@Valid @RequestBody Retour retour, HttpServletRequest requete) {
        Affichage affichage = affichage(requete);
        if (affichage.canal() != Canal.REDIRECTION || !affichage.id().equals(retour.demande())) {
            throw ErreurMetier.introuvable();
        }
        DemandeSuivie demande = suivi.confirmerRetour(suivie(affichage), retour.codeRetour());
        return ouvrirDossier(requete, affichage, demande);
    }

    @DeleteMapping("/demande")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abandonner(HttpServletRequest requete) {
        suivi.annuler(suivie(affichage(requete)));
        SessionParcours.oublierDemande(requete);
    }

    @GetMapping("/dossier")
    VueDossier dossier(HttpServletRequest requete) {
        return dossiers.vue(dossierOuvert(requete));
    }

    /**
     * La pièce reçue d'un registre. Proposée en téléchargement ; {@code apercu=true} l'affiche dans
     * le navigateur quand c'est un PDF.
     */
    @GetMapping("/dossier/pieces/{jeu}")
    ResponseEntity<byte[]> piece(
            @PathVariable @Pattern(regexp = CODE) String jeu,
            @RequestParam(defaultValue = "false") boolean apercu,
            HttpServletRequest requete) {
        return dossiers.piece(dossierOuvert(requete), jeu, apercu);
    }

    /** Une saisie libre du formulaire du service. */
    @PutMapping("/dossier/declarations/{code}")
    VueDossier saisir(
            @PathVariable @Pattern(regexp = CODE) String code,
            @Valid @RequestBody Saisie saisie,
            HttpServletRequest requete) {
        UUID dossier = dossierOuvert(requete);
        declarations.saisir(dossier, code, saisie.valeur());
        return dossiers.vue(dossier);
    }

    /**
     * Une pièce PDF, envoyée telle quelle dans le corps ({@code application/pdf}, 2 Mo au plus).
     * Elle complète le formulaire du service, ou remplace une information qu'un registre n'a
     * définitivement pas transmise ; jamais une information reçue ou attendue.
     */
    @PutMapping(
            path = "/dossier/declarations/{code}/piece",
            consumes = MediaType.APPLICATION_PDF_VALUE)
    VueDossier joindre(
            @PathVariable @Pattern(regexp = CODE) String code,
            @RequestParam(required = false) @Size(max = 255) String nom,
            HttpServletRequest requete)
            throws IOException {
        UUID dossier = dossierOuvert(requete);
        declarations.joindre(
                dossier, code, nom, requete.getContentLengthLong(), requete.getInputStream());
        return dossiers.vue(dossier);
    }

    @GetMapping("/dossier/declarations/{code}/piece")
    ResponseEntity<byte[]> pieceDeclaree(
            @PathVariable @Pattern(regexp = CODE) String code,
            @RequestParam(defaultValue = "false") boolean apercu,
            HttpServletRequest requete) {
        return dossiers.pieceDeclaree(dossierOuvert(requete), code, apercu);
    }

    @DeleteMapping("/dossier/declarations/{code}")
    VueDossier retirer(
            @PathVariable @Pattern(regexp = CODE) String code, HttpServletRequest requete) {
        UUID dossier = dossierOuvert(requete);
        declarations.retirer(dossier, code);
        return dossiers.vue(dossier);
    }

    /**
     * « Déposer mon dossier » : ce que la personne a déclaré est figé. Les réponses des registres
     * encore attendues continuent d'arriver : le dossier est enregistré même si un registre est en
     * panne.
     */
    @PostMapping("/dossier/depot")
    VueDossier deposer(HttpServletRequest requete) {
        UUID dossier = dossierOuvert(requete);
        declarations.deposer(dossier);
        return dossiers.vue(dossier);
    }

    /** « Terminer » : ce navigateur oublie la démarche. Le dossier, lui, reste enregistré. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void terminer(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private Function<String, NouvelleDemande> canal(Ouverture ouverture) {
        String identifiant = ouverture.identifiantUnique();
        return switch (ouverture.canal()) {
            case CODE -> demarche -> NouvelleDemande.parCode(demarche, identifiant);
            case REDIRECTION ->
                    demarche -> NouvelleDemande.parRedirection(demarche, retourUrl, identifiant);
            case NOTIFICATION -> demarche -> NouvelleDemande.parNotification(demarche, identifiant);
        };
    }

    private static Affichage affichage(HttpServletRequest requete) {
        return SessionParcours.demande(requete).orElseThrow(ErreurMetier::introuvable);
    }

    private DemandeSuivie suivie(Affichage affichage) {
        return demandes.lire(affichage.id()).orElseThrow(ErreurMetier::introuvable);
    }

    private static UUID dossierOuvert(HttpServletRequest requete) {
        return SessionParcours.dossier(requete).orElseThrow(ErreurMetier::introuvable);
    }

    /**
     * Le dossier ne s'ouvre que si la personne a donné son accord à la demande de ce navigateur.
     * Une demande acceptée d'office (accès déjà donné à ce service) ne prouve pas que la personne
     * est derrière l'écran : un passant qui tape l'identifiant d'autrui ne lit rien.
     */
    private static VueDemande ouvrirDossier(
            HttpServletRequest requete, Affichage affichage, DemandeSuivie demande) {
        boolean ouvert = affichage.accordAttendu() && demande.dossierId() != null;
        if (ouvert) {
            SessionParcours.ouvrirDossier(requete, demande.dossierId());
        }
        return vue(affichage, demande.etat(), ouvert);
    }

    private static VueDemande vue(Affichage affichage, EtatDemandeCode etat, boolean dossier) {
        return new VueDemande(
                affichage.id(),
                affichage.canal(),
                etat,
                affichage.urlAccord(),
                affichage.codeCourt(),
                affichage.codeVerification(),
                affichage.expireLe(),
                dossier);
    }
}
