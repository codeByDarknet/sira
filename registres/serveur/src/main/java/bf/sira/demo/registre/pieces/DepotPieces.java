package bf.sira.demo.registre.pieces;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Les pièces déposées par les agents pour une personne (table {@code piece_personne}). */
@Repository
public class DepotPieces {

    /** Ce qu'on montre d'une pièce, sans son contenu. */
    public record Piece(
            UUID id,
            String type,
            String titre,
            List<String> champs,
            List<Information> informations,
            String nomFichier,
            String typeMime,
            int taille,
            String deposePar,
            Instant deposeLe) {}

    /**
     * Information saisie par l'agent sur une pièce « autre document ».
     *
     * @param type TEXTE, DATE (AAAA-MM-JJ), NOMBRE ou BOOLEEN
     */
    public record Information(String libelle, String type, String valeur) {}

    static final TypeReference<List<Information>> INFORMATIONS = new TypeReference<>() {};

    /** Le fichier lui-même. */
    public record Contenu(String nomFichier, String typeMime, byte[] octets) {}

    private static final String COLONNES =
            "id, type, titre, champs, informations, nom_fichier, type_mime, taille, depose_par,"
                    + " depose_le";

    private final JdbcClient base;
    private final JsonMapper json;

    DepotPieces(JdbcClient base, JsonMapper json) {
        this.base = base;
        this.json = json;
    }

    public List<Piece> lister(UUID personne) {
        return base.sql(
                        "SELECT "
                                + COLONNES
                                + " FROM piece_personne WHERE personne = ?"
                                + " ORDER BY depose_le DESC")
                .param(personne)
                .query(this::piece)
                .list();
    }

    public UUID ajouter(
            UUID personne,
            String type,
            String titre,
            List<String> champs,
            List<Information> informations,
            String nomFichier,
            String typeMime,
            byte[] octets,
            String deposePar) {
        return base.sql(
                        """
                        INSERT INTO piece_personne
                            (personne, type, titre, champs, informations, nom_fichier, type_mime,
                             taille, contenu, depose_par)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?) RETURNING id
                        """)
                .params(
                        personne,
                        type,
                        titre,
                        String.join(",", champs),
                        json.writeValueAsString(informations),
                        nomFichier,
                        typeMime,
                        octets.length,
                        octets,
                        deposePar)
                .query(UUID.class)
                .single();
    }

    public Optional<Contenu> contenu(UUID personne, UUID piece) {
        return base.sql(
                        """
                        SELECT nom_fichier, type_mime, contenu FROM piece_personne
                         WHERE personne = ? AND id = ?
                        """)
                .params(personne, piece)
                .query((ligne, n) ->
                        new Contenu(ligne.getString(1), ligne.getString(2), ligne.getBytes(3)))
                .optional();
    }

    /** La dernière pièce PDF déposée de ce type, pour la personne désignée par son identifiant. */
    public Optional<Contenu> dernierPdf(String identifiant, String type) {
        return base.sql(
                        """
                        SELECT p.nom_fichier, p.type_mime, p.contenu FROM piece_personne p
                          JOIN personne x ON x.id = p.personne
                         WHERE x.identifiant = ? AND p.type = ? AND p.type_mime = 'application/pdf'
                         ORDER BY p.depose_le DESC LIMIT 1
                        """)
                .params(identifiant, type)
                .query((ligne, n) ->
                        new Contenu(ligne.getString(1), ligne.getString(2), ligne.getBytes(3)))
                .optional();
    }

    public boolean supprimer(UUID personne, UUID piece) {
        return base.sql("DELETE FROM piece_personne WHERE personne = ? AND id = ?")
                        .params(personne, piece)
                        .update()
                == 1;
    }

    private Piece piece(ResultSet ligne, int numero) throws SQLException {
        String champs = ligne.getString("champs");
        return new Piece(
                ligne.getObject("id", UUID.class),
                ligne.getString("type"),
                ligne.getString("titre"),
                champs == null || champs.isBlank()
                        ? List.of()
                        : Arrays.stream(champs.split(",")).toList(),
                json.readValue(ligne.getString("informations"), INFORMATIONS),
                ligne.getString("nom_fichier"),
                ligne.getString("type_mime"),
                ligne.getInt("taille"),
                ligne.getString("depose_par"),
                ligne.getTimestamp("depose_le").toInstant());
    }
}
