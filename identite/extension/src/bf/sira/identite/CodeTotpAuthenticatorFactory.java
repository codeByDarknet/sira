package bf.sira.identite;

import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticatorFactory;
import org.keycloak.models.KeycloakSession;

/** Déclare l'étape « code TOTP » des agents auprès de Keycloak (remplace « auth-otp-form »). */
public class CodeTotpAuthenticatorFactory extends OTPFormAuthenticatorFactory {

    public static final String ID = "sira-code-totp";

    private static final CodeTotpAuthenticator INSTANCE = new CodeTotpAuthenticator();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Sira - code TOTP";
    }

    @Override
    public String getHelpText() {
        return "Code de l'application d'authentification, avec un bouton pour afficher un nouveau"
                + " QR code si le téléphone a été perdu ou changé.";
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return INSTANCE;
    }
}
