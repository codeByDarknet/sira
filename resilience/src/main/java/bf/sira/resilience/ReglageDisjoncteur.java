package bf.sira.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Le disjoncteur de chaque registre, sous le préfixe {@code sira.passerelle.disjoncteur}.
 *
 * @param fenetre nombre d'appels observés
 * @param appelsMinimum appels nécessaires avant de juger
 * @param seuilEchecs pourcentage d'échecs qui ouvre le disjoncteur
 * @param attenteOuvert durée d'ouverture avant un nouvel essai
 * @param appelsSemiOuvert appels d'essai admis avant de refermer
 */
@ConfigurationProperties("sira.passerelle.disjoncteur")
public record ReglageDisjoncteur(
        @DefaultValue("10") int fenetre,
        @DefaultValue("4") int appelsMinimum,
        @DefaultValue("50") float seuilEchecs,
        @DefaultValue("20s") Duration attenteOuvert,
        @DefaultValue("2") int appelsSemiOuvert) {}
