package bf.sira.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * La file d'attente durable, sous le préfixe {@code sira.passerelle.file}.
 *
 * @param intervalle fréquence de relève
 * @param lot éléments pris par relève
 * @param bail durée de réservation d'un élément par l'instance qui le traite
 * @param delaiInitial attente après le premier échec ; elle double à chaque échec
 * @param delaiMax attente maximale entre deux essais
 * @param dureeMax âge au-delà duquel un élément est abandonné ({@code EXPIRE})
 */
@ConfigurationProperties("sira.passerelle.file")
public record ReglageFile(
        @DefaultValue("1s") Duration intervalle,
        @DefaultValue("20") int lot,
        @DefaultValue("30s") Duration bail,
        @DefaultValue("5s") Duration delaiInitial,
        @DefaultValue("5m") Duration delaiMax,
        @DefaultValue("24h") Duration dureeMax) {}
