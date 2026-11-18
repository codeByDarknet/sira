package bf.sira.demo.registre;

import bf.sira.demo.registre.configuration.ProprietesRegistre;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProprietesRegistre.class)
public class RegistreApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistreApplication.class, args);
    }
}
