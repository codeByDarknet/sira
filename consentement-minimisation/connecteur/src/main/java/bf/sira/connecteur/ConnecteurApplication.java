package bf.sira.connecteur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Le seul appelant est reconnu à son certificat : aucun compte à mot de passe n'est créé.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ConnecteurApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnecteurApplication.class, args);
    }
}
