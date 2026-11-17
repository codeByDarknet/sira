package bf.sira.passerelle.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Réglages de la passerelle, sous le préfixe {@code sira.passerelle}.
 *
 * @param espaceCitoyenUrl adresse publique de l'espace citoyen, base de {@code url_accord}
 * @param pseudonymisationCle fichier de la clé de pseudonymisation, généré au premier démarrage
 */
@ConfigurationProperties("sira.passerelle")
public record Proprietes(
        Identite identite,
        String espaceCitoyenUrl,
        Path pseudonymisationCle,
        @DefaultValue Collecte collecte,
        @DefaultValue Purge purge,
        @DefaultValue Essai essai) {

    /**
     * Les deux émetteurs de jetons, chacun sur son préfixe de routes (contrat §4).
     *
     * @param audience valeur exigée dans {@code aud}
     */
    public record Identite(String audience, Royaume citoyens, Royaume socle) {}

    /**
     * @param emetteur valeur exacte de {@code iss} : l'adresse publique du royaume
     * @param cles adresse interne des clés de signature du royaume, lue en mTLS
     */
    public record Royaume(String emetteur, String cles) {}

    /**
     * Les essais, la cloison et la file sont réglés par le module de résilience ({@code
     * bf.sira.resilience}), le délai de réponse par le module de consentement ({@code
     * bf.sira.consentement.ReglagesAccord}), la fréquence des points de contrôle par le journal.
     *
     * @param budgetImmediat temps maximal pendant lequel l'appelant attend l'essai immédiat
     * @param tailleAttestationMax taille maximale d'une réponse de connecteur
     */
    public record Collecte(
            @DefaultValue("4s") Duration budgetImmediat,
            @DefaultValue("8MB") DataSize tailleAttestationMax) {}

    /**
     * @param intervalle fréquence de la purge (demandes expirées, attestations échues)
     */
    public record Purge(@DefaultValue("30s") Duration intervalle) {}

    /**
     * Essai d'un jeu depuis la console, avant sa publication.
     *
     * @param personnesFictives identifiants des seules personnes sur lesquelles un essai est admis
     *     ; liste vide : aucun essai possible
     */
    public record Essai(@DefaultValue List<String> personnesFictives) {}
}
