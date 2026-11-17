package bf.sira.consentement;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de l'accord du citoyen, sous le préfixe {@code sira.passerelle.demandes}.
 *
 * @param dureeAccord temps laissé au citoyen pour décider
 * @param essaisRattachementMax codes courts erronés tolérés par citoyen pendant {@code dureeAccord}
 * @param essaisRattachementGlobalMax codes courts erronés tolérés, tous citoyens confondus, pendant
 *     {@code dureeAccord} : borne les essais répartis sur plusieurs comptes
 * @param essaisVerificationMax codes de vérification erronés tolérés par demande ; au-delà, elle ne
 *     peut plus être acceptée
 */
@ConfigurationProperties("sira.passerelle.demandes")
public record ReglagesAccord(
        @DefaultValue("5m") Duration dureeAccord,
        @DefaultValue("5") int essaisRattachementMax,
        @DefaultValue("1000") int essaisRattachementGlobalMax,
        @DefaultValue("5") int essaisVerificationMax) {}
