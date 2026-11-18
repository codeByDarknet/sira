package bf.sira.commun.agents;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gestion des agents d'une administration, sous le préfixe {@code sira.agents}. Présente seulement
 * sur les serveurs des registres et des services.
 *
 * @param administration code de l'administration : seuls ses agents (attribut {@code
 *     administration} de l'annuaire) entrent, et seuls eux sont listés et gérés ici
 * @param nom nom lisible de l'administration, repris dans le courriel
 * @param expediteur adresse d'envoi des courriels, avec son nom : {@code SIRA<sira@exemple.bf>}
 * @param urlApplication adresse de l'écran, donnée dans le courriel
 * @param premierAgent identifiant de connexion (adresse électronique) qui reçoit le rôle
 *     Administrateur au premier démarrage ; par défaut {@code agent.<administration>@sira.example}
 */
@ConfigurationProperties("sira.agents")
public record ProprietesAgents(
        String administration,
        @DefaultValue("Sira") String nom,
        @DefaultValue("SIRA<ne-pas-repondre@sira.localhost>") String expediteur,
        String urlApplication,
        String premierAgent) {

    public ProprietesAgents {
        if (premierAgent == null || premierAgent.isBlank()) {
            premierAgent = "agent." + administration + "@sira.example";
        }
    }
}
