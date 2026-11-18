package bf.sira.demo.service;

import bf.sira.demo.service.configuration.ProprietesFormulaire;
import bf.sira.demo.service.configuration.ProprietesService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ProprietesService.class, ProprietesFormulaire.class})
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
