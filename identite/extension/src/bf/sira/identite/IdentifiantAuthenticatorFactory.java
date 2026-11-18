package bf.sira.identite;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/** Déclare l'étape « identifiant » du citoyen auprès de Keycloak. */
public class IdentifiantAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "sira-identifiant";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Sira - identifiant";
    }

    @Override
    public String getHelpText() {
        return "Identifiant unique puis code à usage unique, sans révéler si le compte existe.";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] {Requirement.REQUIRED};
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new IdentifiantAuthenticator(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Aucun paramètre.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Aucune initialisation.
    }

    @Override
    public void close() {
        // Sans état.
    }
}
