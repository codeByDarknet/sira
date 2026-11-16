package bf.sira.journal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages du journal, sous le préfixe {@code sira.passerelle.journal}.
 *
 * @param intervallePointsDeControle fréquence des points de contrôle signés
 */
@ConfigurationProperties("sira.passerelle.journal")
public record ReglagesJournal(@DefaultValue("5m") Duration intervallePointsDeControle) {}
