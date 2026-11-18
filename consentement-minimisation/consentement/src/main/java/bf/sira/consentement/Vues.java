package bf.sira.consentement;

import bf.sira.commun.donnees.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ce que le citoyen lit d'une demande ou d'une autorisation : qui demande, quoi et sous quelle
 * forme (contrat §6). Aucune de ces vues ne porte une valeur de registre.
 */
@Component
class Vues {

    record ServiceVu(String code, String nom, String organisme) {}

    record RegistreVu(String code, String nom) {}

    /**
     * @param detail la forme de la réponse, en clair : un oui ou non, des informations, un document
     */
    record JeuVu(String code, String libelle, String niveau, RegistreVu registre, String detail) {}

    private final JdbcClient sql;

    Vues(JdbcClient sql) {
        this.sql = sql;
    }

    static ServiceVu service(ResultSet ligne) throws SQLException {
        return new ServiceVu(
                ligne.getString("service"),
                ligne.getString("service_nom"),
                ligne.getString("organisme"));
    }

    List<JeuVu> jeux(List<String> codes) {
        return sql.sql(
                        "SELECT j.code, j.libelle, j.niveau, j.champs, j.document, r.code AS"
                                + " registre, r.nom AS registre_nom FROM jeu j JOIN registre r ON"
                                + " r.code = j.registre WHERE j.code = ANY (?) ORDER BY j.code")
                .param(Sql.tableau(codes))
                .query((ligne, i) -> {
                    String niveau = ligne.getString("niveau");
                    return new JeuVu(
                            ligne.getString("code"),
                            ligne.getString("libelle"),
                            niveau,
                            new RegistreVu(
                                    ligne.getString("registre"), ligne.getString("registre_nom")),
                            switch (niveau) {
                                case "STATUT" -> "Réponse : oui ou non";
                                case "CHAMPS" ->
                                        "Informations transmises : "
                                                + String.join(", ", Sql.liste(ligne, "champs"));
                                case "DOCUMENT" ->
                                        "Document transmis : " + ligne.getString("document");
                                // La base n'admet que ces trois niveaux.
                                default ->
                                        throw new IllegalStateException(
                                                "Niveau de jeu inconnu : " + niveau);
                            });
                })
                .list();
    }
}
