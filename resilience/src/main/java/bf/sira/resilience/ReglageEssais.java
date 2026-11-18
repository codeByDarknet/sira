package bf.sira.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Les essais immédiats et la cloison, sous le préfixe {@code sira.passerelle.collecte} (le même que
 * la collecte de la passerelle, qui en lit d'autres clés).
 *
 * @param tentatives essais immédiats, le premier compris
 * @param attenteEntreTentatives pause entre deux essais immédiats
 * @param appelsSimultanes appels en cours tolérés vers un même registre
 */
@ConfigurationProperties("sira.passerelle.collecte")
public record ReglageEssais(
        @DefaultValue("2") int tentatives,
        @DefaultValue("200ms") Duration attenteEntreTentatives,
        @DefaultValue("10") int appelsSimultanes) {}
