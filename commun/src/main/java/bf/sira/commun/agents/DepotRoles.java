package bf.sira.commun.agents;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Les rôles de l'administration et le rôle de chaque agent, dans la base de l'application (tables
 * {@code agent_role} et {@code agent_affectation}). Un agent est désigné par son identifiant de
 * connexion, qui ne change jamais dans l'annuaire.
 */
public class DepotRoles {

    private final JdbcClient base;

    public DepotRoles(JdbcClient base) {
        this.base = base;
    }

    /** Un rôle, avec le nombre d'agents qui l'ont. */
    public record Role(
            UUID id,
            String code,
            String libelle,
            String description,
            List<String> permissions,
            boolean systeme,
            long utilisateurs) {}

    public List<Role> lister() {
        return base.sql(
                        """
                        SELECT r.*, (SELECT count(*) FROM agent_affectation a WHERE a.role = r.code)
                               AS utilisateurs
                          FROM agent_role r ORDER BY r.systeme DESC, r.libelle
                        """)
                .query(DepotRoles::role)
                .list();
    }

    public Optional<Role> lire(String code) {
        return base.sql(
                        """
                        SELECT r.*, (SELECT count(*) FROM agent_affectation a WHERE a.role = r.code)
                               AS utilisateurs
                          FROM agent_role r WHERE r.code = ?
                        """)
                .param(code)
                .query(DepotRoles::role)
                .optional();
    }

    public Optional<Role> lireParId(UUID id) {
        return lister().stream().filter(role -> role.id().equals(id)).findFirst();
    }

    /** Ajoute le rôle s'il n'existe pas ; faux s'il existait déjà. */
    public boolean creer(
            String code,
            String libelle,
            String description,
            Set<String> permissions,
            boolean systeme) {
        return base.sql(
                                """
                                INSERT INTO agent_role (code, libelle, description, permissions, systeme)
                                VALUES (?, ?, ?, ?, ?) ON CONFLICT (code) DO NOTHING
                                """)
                        .params(code, libelle, description, joindre(permissions), systeme)
                        .update()
                == 1;
    }

    public boolean modifier(
            String code, String libelle, String description, Set<String> permissions) {
        return base.sql(
                                """
                                UPDATE agent_role SET libelle = ?, description = ?, permissions = ?
                                 WHERE code = ?
                                """)
                        .params(libelle, description, joindre(permissions), code)
                        .update()
                == 1;
    }

    /** Un rôle encore attribué ou créé par le système ne se supprime pas. */
    public boolean supprimer(String code) {
        return base.sql(
                                """
                                DELETE FROM agent_role r WHERE r.code = ? AND NOT r.systeme
                                   AND NOT EXISTS (SELECT 1 FROM agent_affectation a
                                                    WHERE a.role = r.code)
                                """)
                        .param(code)
                        .update()
                == 1;
    }

    /** Le rôle de chaque agent, pour une page de la liste des utilisateurs. */
    public Map<String, String> rolesDe(List<String> identifiants) {
        if (identifiants.isEmpty()) {
            return Map.of();
        }
        return base
                .sql("SELECT identifiant, role FROM agent_affectation WHERE identifiant IN (:ids)")
                .param("ids", identifiants)
                .query((ligne, n) -> Map.entry(ligne.getString(1), ligne.getString(2)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<String> roleDe(String identifiant) {
        return base.sql("SELECT role FROM agent_affectation WHERE identifiant = ?")
                .param(identifiant)
                .query(String.class)
                .optional();
    }

    public void affecter(String identifiant, String role) {
        base.sql(
                        """
                        INSERT INTO agent_affectation (identifiant, role) VALUES (?, ?)
                        ON CONFLICT (identifiant) DO UPDATE SET role = EXCLUDED.role
                        """)
                .params(identifiant, role)
                .update();
    }

    /** Pose le rôle seulement si l'agent n'en a aucun (amorçage). */
    public void affecterSiAbsent(String identifiant, String role) {
        base.sql(
                        """
                        INSERT INTO agent_affectation (identifiant, role) VALUES (?, ?)
                        ON CONFLICT (identifiant) DO NOTHING
                        """)
                .params(identifiant, role)
                .update();
    }

    public void retirer(String identifiant) {
        base.sql("DELETE FROM agent_affectation WHERE identifiant = ?").param(identifiant).update();
    }

    /** Les droits de l'agent : ceux de son rôle, aucun s'il n'en a pas. */
    public Set<String> permissionsDe(String identifiant) {
        return base.sql(
                        """
                        SELECT r.permissions FROM agent_affectation a
                          JOIN agent_role r ON r.code = a.role WHERE a.identifiant = ?
                        """)
                .param(identifiant)
                .query(String.class)
                .optional()
                .map(DepotRoles::separer)
                .map(Set::copyOf)
                .orElse(Set.of());
    }

    private static Role role(ResultSet ligne, int numero) throws SQLException {
        return new Role(
                ligne.getObject("id", UUID.class),
                ligne.getString("code"),
                ligne.getString("libelle"),
                ligne.getString("description"),
                separer(ligne.getString("permissions")),
                ligne.getBoolean("systeme"),
                ligne.getLong("utilisateurs"));
    }

    private static String joindre(Set<String> permissions) {
        return permissions.stream().sorted().collect(Collectors.joining(","));
    }

    private static List<String> separer(String permissions) {
        return permissions == null || permissions.isBlank()
                ? List.of()
                : Arrays.stream(permissions.split(",")).map(String::strip).toList();
    }
}
