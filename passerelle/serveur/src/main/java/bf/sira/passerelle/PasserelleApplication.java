package bf.sira.passerelle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * La passerelle assemble dans un seul processus ses modules : journal, consentement et résilience.
 * Une même transaction couvre ainsi l'accord, l'échange et sa ligne de journal.
 */
@SpringBootApplication(
        scanBasePackages = {
            "bf.sira.passerelle",
            "bf.sira.journal",
            "bf.sira.consentement",
            "bf.sira.resilience"
        })
@ConfigurationPropertiesScan({
    "bf.sira.passerelle",
    "bf.sira.journal",
    "bf.sira.consentement",
    "bf.sira.resilience"
})
@EnableScheduling
public class PasserelleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PasserelleApplication.class, args);
    }
}
