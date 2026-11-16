package bf.sira.commun.agents;

/**
 * Un droit qu'un rôle peut accorder : il protège une route du serveur et décide de ce que l'écran
 * montre (bouton, menu, page).
 *
 * @param code identifiant stable, en majuscules : {@code PERSONNES_AJOUTER}
 * @param libelle ce que le droit permet, dit simplement : « Ajouter une personne »
 * @param groupe rubrique où l'écran des rôles le range : « Personnes »
 */
public record Permission(String code, String libelle, String groupe) {

    public static final String UTILISATEURS_LIRE = "UTILISATEURS_LIRE";
    public static final String UTILISATEURS_GERER = "UTILISATEURS_GERER";
    public static final String ROLES_LIRE = "ROLES_LIRE";
    public static final String ROLES_GERER = "ROLES_GERER";

    /** Les droits sur les agents et les rôles, communs à toutes les administrations. */
    static final java.util.List<Permission> COMMUNES =
            java.util.List.of(
                    new Permission(UTILISATEURS_LIRE, "Voir les utilisateurs", "Utilisateurs"),
                    new Permission(
                            UTILISATEURS_GERER,
                            "Ajouter, modifier et retirer des utilisateurs",
                            "Utilisateurs"),
                    new Permission(ROLES_LIRE, "Voir les rôles", "Rôles"),
                    new Permission(ROLES_GERER, "Créer, modifier et supprimer des rôles", "Rôles"));

    /** Un droit de simple lecture : il entre dans le rôle « Lecteur » créé au démarrage. */
    public boolean lecture() {
        return code.endsWith("_LIRE");
    }
}
