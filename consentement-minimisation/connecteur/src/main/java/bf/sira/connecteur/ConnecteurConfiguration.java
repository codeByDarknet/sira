package bf.sira.connecteur;

import bf.sira.commun.identite.SecuriteMembres;
import bf.sira.connecteur.politique.Politique;
import bf.sira.connecteur.regles.MoteurRegles;
import java.time.Clock;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class ConnecteurConfiguration {

    /** Sonde de santé du conteneur : port de gestion, en boucle locale uniquement. */
    @Bean
    @Order(0)
    SecurityFilterChain sante(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint());
        SecuriteMembres.apiSansEtat(http);
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /** Deux routes, un seul appelant reconnu à son certificat : la passerelle. */
    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http, ProprietesConnecteur proprietes) throws Exception {
        SecuriteMembres.appliquer(http);
        http.authorizeHttpRequests(a ->
                a.requestMatchers(HttpMethod.POST, "/v1/requetes")
                        .hasAuthority(proprietes.appelant().autorite())
                        .requestMatchers(HttpMethod.GET, "/v1/schema")
                        .hasAuthority(proprietes.appelant().autorite())
                        .anyRequest()
                        .denyAll());
        return http.build();
    }

    @Bean
    Politique politique(ProprietesConnecteur proprietes) {
        return Politique.charger(proprietes.politique());
    }

    @Bean
    MoteurRegles moteurRegles(Clock horloge, ProprietesConnecteur proprietes) {
        return new MoteurRegles(horloge, proprietes.fuseau());
    }
}
