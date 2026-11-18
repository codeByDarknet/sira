package bf.sira.commun.config;

import bf.sira.commun.erreurs.GestionnaireErreurs;
import bf.sira.commun.erreurs.LimiteCorps;
import bf.sira.commun.jetons.JetonsRoyaume;
import bf.sira.commun.jose.LecteurAttestation;
import bf.sira.commun.jose.RegistreJti;
import bf.sira.commun.jose.RegistreJtiMemoire;
import bf.sira.commun.jose.ScelleurAttestation;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.mtls.ClientsMtls;
import bf.sira.commun.pki.Pki;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

/** Met à disposition de chaque composant les briques communes, sans autre déclaration. */
@AutoConfiguration
@EnableConfigurationProperties(ProprietesCommun.class)
public class SiraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock horloge() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    GestionnaireErreurs gestionnaireErreurs() {
        return new GestionnaireErreurs();
    }

    @Bean
    FilterRegistrationBean<LimiteCorps> limiteCorps(ProprietesCommun proprietes) {
        FilterRegistrationBean<LimiteCorps> filtre =
                new FilterRegistrationBean<>(
                        new LimiteCorps(proprietes.http().tailleCorpsMax().toBytes()));
        filtre.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filtre;
    }

    /** Briques qui demandent les clés du membre : absentes en mode {@code aucun}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("'${sira.mtls.mode:complet}' != 'aucun'")
    static class Materiel {

        @Bean
        @ConditionalOnMissingBean
        Pki pki(ProprietesCommun proprietes) {
            return new Pki(proprietes.pki().repertoire());
        }

        @Bean
        @ConditionalOnMissingBean
        ClientsMtls clientsMtls(
                SslBundles bundles,
                ProprietesCommun proprietes,
                ObjectProvider<RestClient.Builder> constructeurs) {
            return new ClientsMtls(
                    bundles.getBundle(ReglagesParDefaut.BUNDLE),
                    proprietes.http().delaiConnexion(),
                    proprietes.http().delaiLecture(),
                    () -> constructeurs.getIfAvailable(RestClient::builder));
        }

        @Bean
        @ConditionalOnMissingBean
        RegistreJti registreJti(Clock horloge) {
            return new RegistreJtiMemoire(RegistreJtiMemoire.CAPACITE_PAR_DEFAUT, horloge);
        }

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        VerificateurJws verificateurJws(Pki pki, RegistreJti registreJti, Clock horloge) {
            return new VerificateurJws(pki.autorite(), registreJti, horloge);
        }

        // Les beans ci-dessous lisent le cachet ou la clé de chiffrement : ils ne sont créés que
        // dans les composants qui les injectent.

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        Signataire signataire(Pki pki) {
            return new Signataire(pki.cachet());
        }

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        ScelleurAttestation scelleurAttestation(Signataire signataire, Pki pki, Clock horloge) {
            return new ScelleurAttestation(signataire, pki.autorite(), horloge);
        }

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        LecteurAttestation lecteurAttestation(Pki pki, VerificateurJws verificateur) {
            return new LecteurAttestation(pki.chiffrement(), verificateur);
        }
    }

    /**
     * Validation des jetons côté ressource (la passerelle). Le décodeur seul ne suffit pas : un
     * client OIDC l'apporte aussi, sans la bibliothèque du serveur de ressources dont {@link
     * JetonsRoyaume} a besoin.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name =
                    "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter")
    @ConditionalOnExpression("'${sira.mtls.mode:complet}' != 'aucun'")
    static class Jetons {

        @Bean
        @ConditionalOnMissingBean
        JetonsRoyaume jetonsRoyaume(ClientsMtls clients) {
            return new JetonsRoyaume(clients);
        }
    }
}
