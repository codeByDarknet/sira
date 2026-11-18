package bf.sira.demo.service.parcours;

import bf.sira.kit.Canal;
import bf.sira.kit.DemandeCreee;
import bf.sira.kit.EtatDemandeCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce qui relie un navigateur anonyme à sa démarche : la demande en cours, puis le dossier une fois
 * l'accord donné. Tout reste dans la session du serveur ; le navigateur ne porte qu'un cookie.
 */
final class SessionParcours {

    /**
     * La demande affichée à l'écran : ses codes ne sont remis qu'au navigateur qui l'a ouverte.
     *
     * @param accordAttendu vrai si la demande attendait l'accord à sa création : seul cet accord,
     *     donné après coup par la personne, ouvre le dossier dans ce navigateur
     */
    record Affichage(
            UUID id,
            Canal canal,
            String urlAccord,
            String codeCourt,
            String codeVerification,
            Instant expireLe,
            boolean accordAttendu)
            implements Serializable {}

    private static final String DEMANDE = SessionParcours.class.getName() + ".demande";
    private static final String DOSSIER = SessionParcours.class.getName() + ".dossier";

    private SessionParcours() {}

    /**
     * Une nouvelle démarche dans ce navigateur fait oublier le dossier de la précédente : sur un
     * poste partagé, la personne suivante ne voit pas le dossier de celle qui n'a pas terminé.
     */
    static Affichage retenir(HttpServletRequest requete, Canal canal, DemandeCreee creee) {
        Affichage affichage =
                new Affichage(
                        creee.id(),
                        canal,
                        creee.urlAccord(),
                        creee.codeCourt(),
                        creee.codeVerification(),
                        creee.expireLe(),
                        creee.etat() == EtatDemandeCode.EN_ATTENTE_ACCORD);
        HttpSession session = requete.getSession();
        session.removeAttribute(DOSSIER);
        session.setAttribute(DEMANDE, affichage);
        return affichage;
    }

    static Optional<Affichage> demande(HttpServletRequest requete) {
        return attribut(requete, DEMANDE, Affichage.class);
    }

    static void oublierDemande(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.removeAttribute(DEMANDE);
        }
    }

    static Optional<UUID> dossier(HttpServletRequest requete) {
        return attribut(requete, DOSSIER, UUID.class);
    }

    /**
     * À partir d'ici la session donne accès à des données personnelles : son identifiant change,
     * pour qu'un identifiant connu avant l'accord ne vaille plus rien.
     */
    static void ouvrirDossier(HttpServletRequest requete, UUID dossier) {
        if (dossier(requete).filter(dossier::equals).isEmpty()) {
            requete.changeSessionId();
            requete.getSession().setAttribute(DOSSIER, dossier);
        }
    }

    private static <T> Optional<T> attribut(HttpServletRequest requete, String nom, Class<T> type) {
        HttpSession session = requete.getSession(false);
        return Optional.ofNullable(session)
                .map(s -> s.getAttribute(nom))
                .filter(type::isInstance)
                .map(type::cast);
    }
}
