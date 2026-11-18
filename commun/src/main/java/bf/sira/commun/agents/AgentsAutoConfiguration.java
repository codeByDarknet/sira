package bf.sira.commun.agents;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.oidc.ConnexionOidcAutoConfiguration;
import bf.sira.commun.oidc.ProprietesOidc;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Gestion des agents d'une administration (registre ou service) : rôles et droits en base, comptes
 * dans l'annuaire Keycloak, courriel des identifiants. Active dès que {@code
 * sira.agents.administration} est renseigné ; l'application déclare ses droits par un bean {@link
 * CataloguePermissions} et protège ses routes avec {@link AccesAgents}.
 */
@AutoConfiguration(after = ConnexionOidcAutoConfiguration.class)
@ConditionalOnClass({JdbcClient.class, JavaMailSender.class})
@ConditionalOnProperty("sira.agents.administration")
@EnableConfigurationProperties(ProprietesAgents.class)
@Import(AgentsControleur.class)
public class AgentsAutoConfiguration {

    @Bean
    DepotRoles depotRoles(JdbcClient base) {
        return new DepotRoles(base);
    }

    @Bean
    AccesAgents accesAgents(
            ProprietesAgents proprietes,
            DepotRoles roles,
            ObjectProvider<CataloguePermissions> catalogue) {
        return new AccesAgents(
                proprietes.administration(), roles, catalogue.getIfAvailable(() -> List::of));
    }

    @Bean
    AnnuaireKeycloak annuaireKeycloak(
            ClientsMtls clientsMtls, ProprietesOidc oidc, ProprietesAgents proprietes) {
        return new AnnuaireKeycloak(
                clientsMtls.constructeur(IdentiteMembre.FOURNISSEUR_IDENTITE).build(),
                oidc.urlInterne(),
                oidc.jetonsUri(),
                oidc.clientId(),
                proprietes.administration());
    }

    @Bean
    CourrielIdentifiants courrielIdentifiants(
            ObjectProvider<JavaMailSender> envois, ProprietesAgents proprietes) {
        return new CourrielIdentifiants(envois, proprietes);
    }

    @Bean
    AmorcageAgents amorcageAgents(
            DepotRoles roles, AccesAgents acces, ProprietesAgents proprietes) {
        return new AmorcageAgents(roles, acces, proprietes.premierAgent());
    }
}
