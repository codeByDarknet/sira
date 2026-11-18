package bf.sira.identite;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Second temps de la connexion du citoyen : un code à six chiffres, à usage unique, envoyé sur son
 * téléphone. Il est servi par l'étape {@link IdentifiantAuthenticator} elle-même : l'écran du code
 * est le même, adresse du formulaire comprise, que l'identifiant existe ou non.
 *
 * <p>Le code vit dans la session d'authentification, côté serveur : il n'est valable que dans le
 * navigateur qui l'a demandé, cinq minutes. Une session d'authentification a droit à cinq essais
 * en tout, renvois compris, et à trois renvois ; un identifiant reçoit au plus dix codes par heure.
 * L'envoi passe par la messagerie du royaume ; brancher une passerelle SMS ne demande de changer
 * que {@link #transmettre}.
 *
 * <p>Pour un identifiant inconnu, désactivé ou verrouillé, la session est un « leurre » : même
 * page, mêmes messages, même délai, mais aucun code n'est envoyé et aucun n'est accepté.
 */
final class CodeUsageUnique {

    private static final String NOTE_ETAPE = "sira.code.etape";
    private static final String NOTE_CODE = "sira.code";
    private static final String NOTE_ECHEANCE = "sira.code.echeance";
    private static final String NOTE_ESSAIS = "sira.code.essais";
    private static final String NOTE_RENVOIS = "sira.code.renvois";
    private static final String NOTE_ENVOI = "sira.code.envoi";
    private static final String NOTE_LEURRE = "sira.code.leurre";
    private static final String FORMULAIRE = "sira-code.ftl";

    private static final int LONGUEUR = 6;
    private static final long VALIDITE_MS = 5 * 60 * 1000L;
    private static final int ESSAIS_MAX = 5;
    private static final int RENVOIS_MAX = 3;
    private static final long DELAI_RENVOI_MS = 30 * 1000L;
    private static final int ENVOIS_PAR_HEURE = 10;
    /** Durée minimale d'un envoi, réel ou leurre : le temps de réponse ne trahit pas l'identifiant. */
    private static final long DUREE_ENVOI_MS = 1000L;

    private CodeUsageUnique() {}

    /**
     * Ouvre l'étape du code pour l'identifiant saisi, puis envoie un premier code.
     *
     * @param usager le compte utilisable de cet identifiant, ou {@code null} : session leurre
     */
    static void commencer(AuthenticationFlowContext contexte, String identifiant, UserModel usager) {
        AuthenticationSessionModel session = contexte.getAuthenticationSession();
        session.setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, identifiant);
        // L'identifiant saisi s'affiche en tête de page, tel quel, dans les deux cas.
        session.setAuthNote(AbstractUsernameFormAuthenticator.USERNAME_HIDDEN, "true");
        session.setAuthNote(NOTE_ETAPE, "1");
        if (usager == null) {
            session.setAuthNote(NOTE_LEURRE, "1");
        } else {
            contexte.setUser(usager);
        }
        envoyer(contexte);
    }

    static boolean enCours(AuthenticationFlowContext contexte) {
        return contexte.getAuthenticationSession().getAuthNote(NOTE_ETAPE) != null;
    }

    /** Réaffiche le formulaire du code (page rechargée). */
    static void afficher(AuthenticationFlowContext contexte) {
        contexte.challenge(contexte.form().createForm(FORMULAIRE));
    }

    private static boolean estLeurre(AuthenticationFlowContext contexte) {
        return contexte.getAuthenticationSession().getAuthNote(NOTE_LEURRE) != null;
    }

    static void action(AuthenticationFlowContext contexte) {
        MultivaluedMap<String, String> saisie = contexte.getHttpRequest().getDecodedFormParameters();
        AuthenticationSessionModel session = contexte.getAuthenticationSession();

        if (lire(session, NOTE_ESSAIS) >= ESSAIS_MAX) {
            epuise(contexte);
            return;
        }

        if (saisie.containsKey("renvoyer")) {
            if (lire(session, NOTE_RENVOIS) >= RENVOIS_MAX) {
                epuise(contexte);
            } else if (Time.currentTimeMillis() - lire(session, NOTE_ENVOI) < DELAI_RENVOI_MS) {
                contexte.challenge(contexte.form().setInfo("siraCodeRenvoiTropTot").createForm(FORMULAIRE));
            } else {
                session.setAuthNote(NOTE_RENVOIS, Long.toString(lire(session, NOTE_RENVOIS) + 1));
                envoyer(contexte);
            }
            return;
        }

        // Le compteur d'essais n'est jamais remis à zéro dans la session : renvoyer un code ne
        // rend pas d'essais.
        session.setAuthNote(NOTE_ESSAIS, Long.toString(lire(session, NOTE_ESSAIS) + 1));
        String attendu = session.getAuthNote(NOTE_CODE);
        if (attendu == null || Time.currentTimeMillis() > lire(session, NOTE_ECHEANCE)) {
            session.removeAuthNote(NOTE_CODE);
            contexte.failureChallenge(
                    AuthenticationFlowError.EXPIRED_CODE,
                    contexte.form().setError("siraCodeExpire").createForm(FORMULAIRE));
            return;
        }

        String code = saisie.getFirst("code");
        boolean correct =
                code != null
                        && MessageDigest.isEqual(
                                attendu.getBytes(StandardCharsets.UTF_8),
                                code.trim().getBytes(StandardCharsets.UTF_8));
        if (!correct || estLeurre(contexte)) {
            contexte.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    contexte.form().setError("siraCodeInvalide").createForm(FORMULAIRE));
            return;
        }

        session.removeAuthNote(NOTE_CODE);
        contexte.success();
    }

    /** Tire un nouveau code (l'ancien devient caduc), l'envoie et affiche le formulaire de saisie. */
    private static void envoyer(AuthenticationFlowContext contexte) {
        long debut = Time.currentTimeMillis();
        boolean leurre = estLeurre(contexte);
        AuthenticationSessionModel session = contexte.getAuthenticationSession();
        if (!compterEnvoi(contexte)) {
            session.removeAuthNote(NOTE_CODE);
            attendre(debut);
            contexte.challenge(contexte.form().setError("siraCodeQuota").createForm(FORMULAIRE));
            return;
        }

        String code = SecretGenerator.getInstance().randomString(LONGUEUR, SecretGenerator.DIGITS);
        session.setAuthNote(NOTE_CODE, code);
        session.setAuthNote(NOTE_ECHEANCE, Long.toString(debut + VALIDITE_MS));
        session.setAuthNote(NOTE_ENVOI, Long.toString(debut));

        try {
            if (!leurre) {
                transmettre(contexte, contexte.getUser(), code);
            }
        } catch (EmailException e) {
            session.removeAuthNote(NOTE_CODE);
            contexte.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    contexte.form()
                            .setError("siraCodeEnvoiImpossible")
                            .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }
        attendre(debut);
        contexte.challenge(contexte.form().createForm(FORMULAIRE));
    }

    /** Seul point d'envoi : à remplacer par l'appel d'une passerelle SMS en production. */
    private static void transmettre(AuthenticationFlowContext contexte, UserModel usager, String code)
            throws EmailException {
        String consigne = "Il expire dans 5 minutes. Ne le communiquez à personne.";
        contexte.getSession()
                .getProvider(EmailSenderProvider.class)
                .send(
                        contexte.getRealm().getSmtpConfig(),
                        usager,
                        "Sira - votre code de connexion",
                        "Votre code de connexion Sira : " + code + ".\n" + consigne,
                        "<p>Votre code de connexion Sira : <strong>" + code + "</strong>.</p><p>" + consigne + "</p>");
    }

    /**
     * Compte un envoi pour l'identifiant saisi, connu ou non, sur l'heure en cours (partagé entre
     * navigateurs et sessions). Renvoie faux au-delà du plafond.
     */
    private static boolean compterEnvoi(AuthenticationFlowContext contexte) {
        String identifiant =
                contexte.getAuthenticationSession().getAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME);
        if (identifiant == null && contexte.getUser() != null) {
            identifiant = contexte.getUser().getUsername();
        }
        long heure = Time.currentTimeMillis() / 3_600_000;
        String cle = "sira.envois:" + contexte.getRealm().getId() + ":"
                + String.valueOf(identifiant).trim().toLowerCase(Locale.ROOT) + ":" + heure;
        SingleUseObjectProvider compteurs = contexte.getSession().singleUseObjects();
        Map<String, String> actuel = compteurs.get(cle);
        long envois = actuel == null ? 0 : Long.parseLong(actuel.getOrDefault("n", "0"));
        if (envois >= ENVOIS_PAR_HEURE) {
            return false;
        }
        compteurs.put(cle, 3600, Map.of("n", Long.toString(envois + 1)));
        return true;
    }

    private static void epuise(AuthenticationFlowContext contexte) {
        contexte.getAuthenticationSession().removeAuthNote(NOTE_CODE);
        contexte.failureChallenge(
                AuthenticationFlowError.INVALID_CREDENTIALS,
                contexte.form().setError("siraCodeEpuise").createForm(FORMULAIRE));
    }

    private static void attendre(long debut) {
        long reste = debut + DUREE_ENVOI_MS - Time.currentTimeMillis();
        if (reste > 0) {
            try {
                Thread.sleep(reste);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long lire(AuthenticationSessionModel session, String note) {
        String valeur = session.getAuthNote(note);
        return valeur == null ? 0 : Long.parseLong(valeur);
    }
}
