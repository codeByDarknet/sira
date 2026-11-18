package bf.sira.identite;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.UsernameForm;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.managers.BruteForceProtector;

/**
 * Connexion du citoyen : son identifiant unique, puis le code à usage unique reçu sur son téléphone
 * (voir {@link CodeUsageUnique}). Les deux temps sont servis par cette seule étape, qu'il existe un
 * compte utilisable ou non : rien dans la page, ni l'adresse du formulaire, ne révèle l'identifiant.
 * Une clé d'accès (sans identifiant saisi) passe par le formulaire de Keycloak et se passe de code.
 */
public class IdentifiantAuthenticator implements Authenticator {

    private final UsernameForm formulaire;

    IdentifiantAuthenticator(KeycloakSession session) {
        this.formulaire = new UsernameForm(session);
    }

    @Override
    public void authenticate(AuthenticationFlowContext contexte) {
        if (CodeUsageUnique.enCours(contexte)) {
            CodeUsageUnique.afficher(contexte);
        } else {
            formulaire.authenticate(contexte);
        }
    }

    @Override
    public void action(AuthenticationFlowContext contexte) {
        if (CodeUsageUnique.enCours(contexte)) {
            CodeUsageUnique.action(contexte);
            return;
        }
        MultivaluedMap<String, String> saisie = contexte.getHttpRequest().getDecodedFormParameters();
        String identifiant = saisie.getFirst("username");
        // Identifiant vide ou clé d'accès : le formulaire de Keycloak s'en charge.
        if (identifiant == null || identifiant.isBlank()) {
            formulaire.action(contexte);
            return;
        }
        CodeUsageUnique.commencer(contexte, identifiant.trim(), compteUtilisable(contexte, identifiant.trim()));
    }

    /** Le compte de cet identifiant s'il peut recevoir un code (actif, non verrouillé), sinon null. */
    private static UserModel compteUtilisable(AuthenticationFlowContext contexte, String identifiant) {
        KeycloakSession session = contexte.getSession();
        RealmModel royaume = contexte.getRealm();
        UserModel usager = KeycloakModelUtils.findUserByNameOrEmail(session, royaume, identifiant);
        if (usager == null || !usager.isEnabled() || usager.getEmail() == null) {
            return null;
        }
        BruteForceProtector verrou = session.getProvider(BruteForceProtector.class);
        boolean verrouille = royaume.isBruteForceProtected()
                && (verrou.isTemporarilyDisabled(session, royaume, usager)
                        || verrou.isPermanentlyLockedOut(session, royaume, usager));
        return verrouille ? null : usager;
    }
    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel royaume, UserModel usager) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel royaume, UserModel usager) {
        // Aucune action requise.
    }

    @Override
    public void close() {
        formulaire.close();
    }
}
