package bf.sira.passerelle.collecte;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jetons.Acteur;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.pki.Pki;
import bf.sira.journal.Journal;
import bf.sira.journal.Trace;
import bf.sira.passerelle.config.Proprietes;
import bf.sira.passerelle.referentiel.Jeu;
import bf.sira.passerelle.referentiel.Jeux;
import bf.sira.passerelle.referentiel.Registre;
import bf.sira.passerelle.referentiel.Registres;
import bf.sira.resilience.ConnecteurInjoignable;
import bf.sira.resilience.Disjoncteurs;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Essaie un jeu sur une personne fictive avant de le publier (API, « Console »). La requête suit le
 * chemin d'une vraie requête, mais c'est la passerelle qui se désigne comme demandeur : la réponse
 * est chiffrée pour elle, et elle seule peut la montrer à l'instructeur.
 */
@Component
public class EssaiJeu {

    /** Ce qui tient lieu d'accord et de démarche : une personne fictive ne consent à rien. */
    private static final String ESSAI = "essai";

    private final Map<String, Transport> transports;
    private final Requetes requetes;
    private final Disjoncteurs disjoncteurs;
    private final Registres registres;
    private final LecteurAttestation lecteur;
    private final Pki pki;
    private final Journal journal;
    private final List<String> personnesFictives;

    EssaiJeu(
            List<Transport> transports,
            Requetes requetes,
            Disjoncteurs disjoncteurs,
            Registres registres,
            LecteurAttestation lecteur,
            Pki pki,
            Journal journal,
            Proprietes proprietes) {
        this.transports = Transport.parCode(transports);
        this.requetes = requetes;
        this.disjoncteurs = disjoncteurs;
        this.registres = registres;
        this.lecteur = lecteur;
        this.pki = pki;
        this.journal = journal;
        this.personnesFictives = proprietes.essai().personnesFictives();
    }

    /**
     * @return le contenu de {@code resultat} de l'attestation : rien que le niveau demandé
     */
    public Map<String, Object> essayer(Jeu jeu, String identifiantUnique, Acteur acteur) {
        Jeux.verifierForme(jeu);
        if (!personnesFictives.contains(identifiantUnique)) {
            throw new SaisieRefusee(
                    "identifiant_unique",
                    "Un essai ne porte que sur une personne fictive déclarée pour cet usage.");
        }
        Registre registre =
                registres
                        .trouver(jeu.registre())
                        .orElseThrow(
                                () -> new SaisieRefusee("registre", "Ce registre n'existe pas."));
        Transport transport = transports.get(registre.transport());
        if (transport == null) {
            throw indisponible();
        }
        journal.ecrire(Trace.administration(acteur, "jeu:" + jeu.code(), "ESSAI"));
        Transport.Reponse reponse = appeler(registre, transport, jeu, identifiantUnique);
        return switch (HttpStatus.resolve(reponse.statut())) {
            case OK -> resultat(reponse.attestation(), registre.code());
            case NOT_FOUND ->
                    throw ErreurMetier.conflit("Le registre ne connaît pas cette personne.");
            case null, default -> throw ErreurMetier.conflit("Le registre refuse cette question.");
        };
    }

    private Transport.Reponse appeler(
            Registre registre, Transport transport, Jeu jeu, String identifiantUnique) {
        try {
            return disjoncteurs.appeler(
                    registre.code(),
                    true,
                    () ->
                            Collecte.verifier(
                                    transport.envoyer(
                                            registre.code(),
                                            registre.adresse(),
                                            requetes.signer(
                                                    new Requetes.Demande(
                                                            UUID.randomUUID().toString(),
                                                            registre.code(),
                                                            identifiantUnique,
                                                            ESSAI,
                                                            IdentiteMembre.PASSERELLE,
                                                            Certificats.versPem(
                                                                    pki.chiffrement().certificat()),
                                                            ESSAI,
                                                            "Essai d'un jeu avant sa publication.",
                                                            ESSAI,
                                                            null,
                                                            null,
                                                            jeu)))));
        } catch (ConnecteurInjoignable | CallNotPermittedException | BulkheadFullException e) {
            throw indisponible();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultat(String attestation, String registre) {
        Object resultat =
                lecteur.ouvrir(attestation, IdentiteMembre.registre(registre)).getClaim("resultat");
        if (!(resultat instanceof Map<?, ?> contenu)) {
            throw indisponible();
        }
        Map<String, Object> visible = new LinkedHashMap<>((Map<String, Object>) contenu);
        // Un document d'essai se décrit ; son contenu n'a pas à traverser la console.
        if (visible.get("document") instanceof Map<?, ?> document) {
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("nom", document.get("nom"));
            description.put("type", document.get("type"));
            visible.put("document", description);
        }
        return visible;
    }

    private static ErreurMetier indisponible() {
        return new ErreurMetier(
                HttpStatus.SERVICE_UNAVAILABLE,
                "registre-indisponible",
                "Registre indisponible",
                "Le registre ne répond pas pour l'instant. Réessayez dans un moment.");
    }
}
