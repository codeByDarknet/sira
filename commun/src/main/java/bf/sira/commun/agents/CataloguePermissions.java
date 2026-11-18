package bf.sira.commun.agents;

import java.util.List;

/**
 * Les droits propres à une application (registre, service). Chaque application déclare un bean de
 * ce type ; les droits sur les utilisateurs et les rôles s'y ajoutent d'office.
 */
@FunctionalInterface
public interface CataloguePermissions {

    List<Permission> permissions();
}
