package bf.sira.demo.service.demandes;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.jose.JoseRefuse;
import bf.sira.demo.service.configuration.ProprietesService;
import bf.sira.demo.service.dossiers.DepotDossiers;
import bf.sira.demo.service.dossiers.Dossier;
import bf.sira.kit.Attestation;
import bf.sira.kit.AttestationInvalide;
import bf.sira.kit.ClientSira;
import bf.sira.kit.DemandeCreee;
import bf.sira.kit.ErreurPasserelle;
import bf.sira.kit.EtatDemande;
import bf.sira.kit.EtatDemandeCode;
import bf.sira.kit.EtatElement;
import bf.sira.kit.Jeu;
import bf.sira.kit.NouvelleDemande;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

/**
 * Le lien du service avec Sira : ouvrir une demande, la suivre, et ranger dans le dossier du
 * citoyen ce que les registres attestent. Rien n'entre au dossier sans une attestation vérifiée.
 */
@Service
public class SuiviDemandes {

    private static final Logger journal = LoggerFactory.getLogger(SuiviDemandes.class);

    private final ClientSira sira;
    private final DepotDemandes demandes;
    private final DepotDossiers dossiers;
    private final ProprietesService service;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    SuiviDemandes(
            ClientSira sira,
            DepotDemandes demandes,
            DepotDossiers dossiers,
            ProprietesService service,
            TransactionTemplate transaction,
            Clock horloge) {
        this.sira = sira;
        this.demandes = demandes;
        this.dossiers = dossiers;
        this.service = service;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    /**
     * Ouvre une demande pour la démarche du service.
     *
     * @param dossier le dossier concerné quand un agent redemande une vérification, sinon {@code
     *     null} : le citoyen n'est connu qu'après son accord
     */
    public DemandeCreee ouvrir(
            Function<String, NouvelleDemande> canal, Origine origine, UUID dossier) {
        NouvelleDemande nouvelle = canal.apply(service.demarche());
        DemandeCreee creee =
                appeler(
                        () -> sira.creerDemande(nouvelle, UUID.randomUUID().toString()),
                        () ->
                                new ErreurMetier(
                                        HttpStatus.UNPROCESSABLE_CONTENT,
                                        "demande-impossible",
                                        "Demande impossible",
                                        "Sira n'a pas pu enregistrer cette demande."
                                                + " Vérifiez votre saisie, puis réessayez."));
        demandes.ajouter(
                new DemandeSuivie(
                        creee.id(),
                        nouvelle.canal(),
                        origine,
                        creee.etat(),
                        creee.jeux(),
                        null,
                        dossier,
                        horloge.instant(),
                        creee.expireLe()));
        journal.info("Demande ouverte : id={} canal={}", creee.id(), nouvelle.canal());
        return creee;
    }

    /** Relit la demande auprès de Sira et range au dossier ce qui est arrivé. */
    public DemandeSuivie synchroniser(DemandeSuivie demande) {
        if (demande.etat().estFinal()) {
            return demande;
        }
        String code = demande.codeRetour();
        EtatDemande etat =
                appeler(
                        () ->
                                code == null
                                        ? sira.lireDemande(demande.id())
                                        : sira.lireDemande(demande.id(), code),
                        ErreurMetier::introuvable);
        return integrer(demande, etat, code);
    }

    /**
     * Retour du navigateur après l'écran d'accord : le code présenté est celui que seule la
     * passerelle peut confirmer. Un code qu'elle refuse ne donne accès à rien.
     */
    public DemandeSuivie confirmerRetour(DemandeSuivie demande, String codeRetour) {
        EtatDemande etat =
                appeler(
                        () -> sira.lireDemande(demande.id(), codeRetour),
                        () ->
                                new ErreurMetier(
                                        HttpStatus.FORBIDDEN,
                                        "code-invalide",
                                        "Retour refusé",
                                        "Ce retour ne correspond pas à votre demande."
                                                + " Recommencez la démarche."));
        return integrer(demande, etat, codeRetour);
    }

    public void annuler(DemandeSuivie demande) {
        if (demande.etat() != EtatDemandeCode.EN_ATTENTE_ACCORD) {
            return;
        }
        appeler(
                () -> {
                    sira.annulerDemande(demande.id());
                    return null;
                },
                ErreurMetier::introuvable);
        demandes.noter(demande.id(), EtatDemandeCode.ANNULEE, demande.dossierId(), null);
    }

    private DemandeSuivie integrer(DemandeSuivie demande, EtatDemande etat, String codeRetour) {
        return transaction.execute(statut -> {
            UUID dossier = demande.dossierId();
            if (accordDonne(etat)) {
                dossier = ranger(demande, etat);
            }
            // Le code de retour ne sert plus dès que tout est arrivé.
            String code = etat.etat().estFinal() ? null : codeRetour;
            demandes.noter(demande.id(), etat.etat(), dossier, code);
            return demandes.lire(demande.id()).orElseThrow();
        });
    }

    private static boolean accordDonne(EtatDemande etat) {
        return etat.pseudonyme() != null
                && switch (etat.etat()) {
                    case EN_COURS, PARTIELLE, COMPLETE -> true;
                    default -> false;
                };
    }

    private UUID ranger(DemandeSuivie demande, EtatDemande etat) {
        Dossier dossier = dossiers.pourPseudonyme(etat.pseudonyme());
        if (demande.dossierId() != null && !demande.dossierId().equals(dossier.id())) {
            // Une vérification redemandée par un agent ne peut alimenter que le dossier visé.
            throw new IllegalStateException("la demande ne concerne pas le dossier attendu");
        }
        Map<String, EtatDemande.Element> recus =
                etat.elements().stream()
                        .collect(Collectors.toMap(EtatDemande.Element::jeu, e -> e, (a, b) -> a));
        for (Jeu jeu : demande.jeux()) {
            EtatDemande.Element element = recus.get(jeu.code());
            if (element == null || element.etat() == EtatElement.A_TRAITER) {
                dossiers.attendre(dossier.id(), jeu);
            } else if (element.etat().porteUneAttestation() && element.attestation() != null) {
                recevoir(demande, etat, dossier, jeu, element);
            } else {
                dossiers.sansReponse(dossier.id(), jeu, element.etat());
            }
        }
        dossiers.actualiser(dossier.id());
        return dossier.id();
    }

    /**
     * La passerelle ne fait que transporter : l'attestation doit être signée par le registre qui
     * détient le jeu, chiffrée pour ce service, et parler de ce citoyen, de cette démarche, de ce
     * jeu et de ce niveau (le kit le vérifie). Sinon elle est écartée, et la condition reste à
     * examiner par un agent.
     */
    private void recevoir(
            DemandeSuivie demande,
            EtatDemande etat,
            Dossier dossier,
            Jeu jeu,
            EtatDemande.Element element) {
        try {
            Optional<Attestation> attestation = sira.ouvrir(etat, service.demarche(), jeu);
            if (attestation.isPresent()) {
                dossiers.recevoir(dossier.id(), jeu, element.etat(), attestation.get());
                return;
            }
        } catch (JoseRefuse refus) {
            journal.warn(
                    "Attestation écartée : demande={} jeu={} motif={}",
                    demande.id(),
                    jeu.code(),
                    refus.motif());
        } catch (AttestationInvalide refus) {
            journal.warn(
                    "Attestation écartée, elle ne concerne pas cette demande : demande={} jeu={}",
                    demande.id(),
                    jeu.code());
        }
        dossiers.sansReponse(dossier.id(), jeu, EtatElement.REFUSE);
    }

    /**
     * Une panne ou un refus de la passerelle ne montre jamais son détail au navigateur.
     *
     * @param siRefus l'erreur rendue quand la passerelle refuse l'appel lui-même (4xx)
     */
    private <T> T appeler(Supplier<T> appel, Supplier<ErreurMetier> siRefus) {
        try {
            return appel.get();
        } catch (ErreurPasserelle refus) {
            journal.warn(
                    "Refus de la passerelle : statut={} type={}", refus.statut(), refus.type());
            throw refus.statut() < 500 ? siRefus.get() : indisponible();
        } catch (ResourceAccessException panne) {
            journal.warn("Passerelle injoignable");
            throw indisponible();
        }
    }

    private static ErreurMetier indisponible() {
        return new ErreurMetier(
                HttpStatus.SERVICE_UNAVAILABLE,
                "indisponible",
                "Service momentanément indisponible",
                "Sira ne répond pas pour le moment. Réessayez dans un instant.");
    }
}
