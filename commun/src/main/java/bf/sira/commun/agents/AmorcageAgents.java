package bf.sira.commun.agents;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Les rôles de départ, posés au démarrage s'ils manquent (jamais écrasés) :
 *
 * <ul>
 *   <li>Administrateur : tous les droits, attribué au premier agent de l'administration ({@code
 *       agent.<code>}) ;
 *   <li>Agent : tous les droits du métier, sans la gestion des utilisateurs et des rôles ;
 *   <li>Lecteur : la seule consultation.
 * </ul>
 *
 * L'administrateur garde toujours tous les droits, y compris ceux ajoutés par une version suivante
 * de l'application.
 */
class AmorcageAgents implements ApplicationRunner {

    static final String ADMINISTRATEUR = "administrateur";

    private final DepotRoles roles;
    private final AccesAgents acces;
    private final String premierAgent;

    AmorcageAgents(DepotRoles roles, AccesAgents acces, String premierAgent) {
        this.roles = roles;
        this.acces = acces;
        this.premierAgent = premierAgent;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Set<String> tous = codes(acces.catalogue().stream().map(Permission::code));
        Set<String> metier =
                codes(
                        acces.catalogue().stream()
                                .filter(p -> !Permission.COMMUNES.contains(p))
                                .map(Permission::code));
        Set<String> lecture =
                codes(acces.catalogue().stream().filter(Permission::lecture).map(Permission::code));
        if (!roles.creer(
                ADMINISTRATEUR,
                "Administrateur",
                "Tous les droits, y compris la gestion des utilisateurs et des rôles.",
                tous,
                true)) {
            roles.lire(ADMINISTRATEUR)
                    .ifPresent(r -> roles.modifier(r.code(), r.libelle(), r.description(), tous));
        }
        roles.creer(
                "agent",
                "Agent",
                "Le travail courant, sans gérer les utilisateurs ni les rôles.",
                metier,
                true);
        roles.creer(
                "lecteur", "Lecteur", "Consultation seule, aucune modification.", lecture, true);
        // Le premier agent de l'administration se connecte avec son adresse électronique.
        roles.affecterSiAbsent(premierAgent, ADMINISTRATEUR);
    }

    private static Set<String> codes(java.util.stream.Stream<String> codes) {
        return codes.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
