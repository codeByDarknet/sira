package bf.sira.passerelle.config;

import bf.sira.commun.crypto.Pseudonymes;
import bf.sira.commun.donnees.Pages;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
class Composants {

    @Bean
    Pseudonymes pseudonymes(Proprietes proprietes) {
        return Pseudonymes.depuisFichier(proprietes.pseudonymisationCle());
    }

    /** La lecture paginée des listes, partagée par la passerelle et ses modules. */
    @Bean
    Pages pages(JdbcClient sql) {
        return new Pages(sql);
    }

    /** Source de tous les codes remis aux services et aux citoyens. */
    @Bean
    SecureRandom aleatoire() {
        return new SecureRandom();
    }

    /** Les appels aux connecteurs attendent le réseau : un thread virtuel par appel. */
    @Bean(destroyMethod = "close")
    ExecutorService executeurCollecte() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
