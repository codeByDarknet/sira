package bf.sira.identite;

import jakarta.ws.rs.core.Response;
import java.util.List;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;

/**
 * Page du code TOTP, avec en plus un bouton « Afficher un nouveau QR code » pour le téléphone perdu
 * ou changé.
 *
 * <p>Ce bouton ne demande aucune autre vérification : la personne vient de taper son mot de passe
 * (étape obligatoire placée avant celle-ci dans le parcours). Il retire l'ancienne application
 * d'authentification puis fait afficher le QR code, comme à la première connexion.
 */
public class CodeTotpAuthenticator extends OTPFormAuthenticator {

    /** Nom du bouton envoyé par la page du code. */
    static final String NOUVEAU_QR = "sira-nouveau-qr";

    @Override
    public void action(AuthenticationFlowContext context) {
        if (!context.getHttpRequest().getDecodedFormParameters().containsKey(NOUVEAU_QR)) {
            super.action(context);
            return;
        }
        UserModel utilisateur = context.getUser();
        List<String> anciennes =
                utilisateur
                        .credentialManager()
                        .getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                        .map(credential -> credential.getId())
                        .toList();
        anciennes.forEach(utilisateur.credentialManager()::removeStoredCredentialById);
        context.getAuthenticationSession()
                .addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
        context.success();
    }

    @Override
    protected Response createLoginForm(LoginFormsProvider form) {
        // Dit à la page qu'elle peut montrer le bouton « Afficher un nouveau QR code ».
        return super.createLoginForm(form.setAttribute("siraNouveauQr", Boolean.TRUE));
    }
}
