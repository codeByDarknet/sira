package bf.sira.demo.service.dossiers;

import bf.sira.demo.service.configuration.ProprietesFormulaire.Nature;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Ce que la personne déclare. Aucune écriture n'aboutit sur un dossier déjà déposé. */
@Repository
class DepotDeclarations {

    private final JdbcClient base;
    private final Clock horloge;

    DepotDeclarations(JdbcClient base, Clock horloge) {
        this.base = base;
        this.horloge = horloge;
    }

    List<Declaration> lister(UUID dossier) {
        return base.sql(
                        """
                        SELECT code, libelle, nature, remplace, valeur, piece_nom, piece_type,
                               octet_length(piece) AS taille, modifiee_le
                          FROM declaration WHERE dossier_id = ? ORDER BY code
                        """)
                .param(dossier)
                .query(DepotDeclarations::declaration)
                .list();
    }

    Optional<DepotDossiers.Contenu> piece(UUID dossier, String code) {
        return base.sql(
                        """
                        SELECT piece_nom, piece_type, piece FROM declaration
                         WHERE dossier_id = ? AND code = ? AND piece IS NOT NULL
                        """)
                .params(dossier, code)
                .query((ligne, numero) ->
                        new DepotDossiers.Contenu(
                                ligne.getString("piece_nom"),
                                ligne.getString("piece_type"),
                                ligne.getBytes("piece")))
                .optional();
    }

    /** Écrit la déclaration ; rend faux si le dossier est déjà déposé. */
    boolean ecrire(
            UUID dossier,
            String code,
            String libelle,
            Nature nature,
            boolean remplace,
            String valeur,
            String nomPiece,
            byte[] piece) {
        return base.sql(
                                """
                                INSERT INTO declaration (dossier_id, code, libelle, nature, remplace,
                                                         valeur, piece_nom, piece_type, piece,
                                                         modifiee_le)
                                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                                 WHERE EXISTS (SELECT 1 FROM dossier
                                                WHERE id = ? AND depose_le IS NULL)
                                ON CONFLICT (dossier_id, code)
                                DO UPDATE SET libelle = EXCLUDED.libelle, nature = EXCLUDED.nature,
                                              remplace = EXCLUDED.remplace,
                                              valeur = EXCLUDED.valeur,
                                              piece_nom = EXCLUDED.piece_nom,
                                              piece_type = EXCLUDED.piece_type,
                                              piece = EXCLUDED.piece,
                                              modifiee_le = EXCLUDED.modifiee_le
                                """)
                        .params(
                                dossier,
                                code,
                                libelle,
                                nature.name(),
                                remplace,
                                valeur,
                                nomPiece,
                                piece == null ? null : "application/pdf",
                                piece,
                                OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC),
                                dossier)
                        .update()
                == 1;
    }

    /** Retire la déclaration ; rend faux si le dossier est déjà déposé. */
    boolean retirer(UUID dossier, String code) {
        base.sql(
                        """
                        DELETE FROM declaration
                         WHERE dossier_id = ? AND code = ?
                           AND EXISTS (SELECT 1 FROM dossier WHERE id = ? AND depose_le IS NULL)
                        """)
                .params(dossier, code, dossier)
                .update();
        return base.sql("SELECT depose_le IS NULL FROM dossier WHERE id = ?")
                .param(dossier)
                .query(Boolean.class)
                .single();
    }

    private static Declaration declaration(ResultSet ligne, int numero) throws SQLException {
        String nomPiece = ligne.getString("piece_nom");
        return new Declaration(
                ligne.getString("code"),
                ligne.getString("libelle"),
                Nature.valueOf(ligne.getString("nature")),
                ligne.getBoolean("remplace"),
                ligne.getString("valeur"),
                nomPiece == null
                        ? null
                        : new Verification.Piece(
                                nomPiece, ligne.getString("piece_type"), ligne.getLong("taille")),
                ligne.getTimestamp("modifiee_le").toInstant());
    }
}
