package bf.sira.demo.service.dossiers;

import bf.sira.demo.service.Page;
import bf.sira.demo.service.configuration.ProprietesService;
import bf.sira.kit.Attestation;
import bf.sira.kit.EtatElement;
import bf.sira.kit.Jeu;
import bf.sira.kit.Niveau;
import bf.sira.kit.Resultat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class DepotDossiers {

    private static final TypeReference<Map<String, Object>> CHAMPS = new TypeReference<>() {};
    private static final String COLONNES_DOSSIER =
            "id, numero, pseudonyme, etat, cree_le, modifie_le, depose_le";

    /** Le contenu d'une pièce, tel que le registre l'a remis. */
    public record Contenu(String nom, String type, byte[] octets) {}

    private final JdbcClient base;
    private final JsonMapper json;
    private final Clock horloge;
    private final ProprietesService service;

    DepotDossiers(JdbcClient base, JsonMapper json, Clock horloge, ProprietesService service) {
        this.base = base;
        this.json = json;
        this.horloge = horloge;
        this.service = service;
    }

    /** Le dossier du citoyen ; il est ouvert à sa première venue. */
    public Dossier pourPseudonyme(String pseudonyme) {
        OffsetDateTime maintenant = maintenant();
        base.sql(
                        """
                        INSERT INTO dossier (id, numero, pseudonyme, etat, cree_le, modifie_le)
                        VALUES (?, ? || lpad(nextval('numero_dossier')::text, 6, '0'), ?,
                                'EN_VERIFICATION', ?, ?)
                        ON CONFLICT (pseudonyme) DO NOTHING
                        """)
                .params(
                        UUID.randomUUID(),
                        "%s-%d-".formatted(service.prefixeDossier(), maintenant.getYear()),
                        pseudonyme,
                        maintenant,
                        maintenant)
                .update();
        return base.sql("SELECT " + COLONNES_DOSSIER + " FROM dossier WHERE pseudonyme = ?")
                .param(pseudonyme)
                .query(DepotDossiers::dossier)
                .single();
    }

    public Optional<Dossier> lire(UUID id) {
        return base.sql("SELECT " + COLONNES_DOSSIER + " FROM dossier WHERE id = ?")
                .param(id)
                .query(DepotDossiers::dossier)
                .optional();
    }

    public Page<Dossier> lister(EtatDossier etat, int page, int taille) {
        String filtre = etat == null ? null : etat.name();
        long total =
                base.sql("SELECT count(*) FROM dossier WHERE (?::text IS NULL OR etat = ?)")
                        .params(filtre, filtre)
                        .query(Long.class)
                        .single();
        List<Dossier> elements =
                base.sql(
                                "SELECT "
                                        + COLONNES_DOSSIER
                                        + """
                                         FROM dossier WHERE (?::text IS NULL OR etat = ?)
                                         ORDER BY modifie_le DESC, id LIMIT ? OFFSET ?
                                        """)
                        .params(filtre, filtre, taille, Page.decalage(page, taille))
                        .query(DepotDossiers::dossier)
                        .list();
        return new Page<>(elements, page, taille, total);
    }

    public List<Verification> verifications(UUID dossier) {
        return base.sql(
                        """
                        SELECT jeu, libelle, niveau, registre, etat, id_echange, valeur, champs,
                               document_nom, document_type, octet_length(document) AS taille,
                               modifiee_le
                          FROM verification WHERE dossier_id = ? ORDER BY registre, jeu
                        """)
                .param(dossier)
                .query(this::verification)
                .list();
    }

    public Optional<Contenu> piece(UUID dossier, String jeu) {
        return base.sql(
                        """
                        SELECT document_nom, document_type, document FROM verification
                         WHERE dossier_id = ? AND jeu = ? AND document IS NOT NULL
                        """)
                .params(dossier, jeu)
                .query((ligne, numero) ->
                        new Contenu(
                                ligne.getString("document_nom"),
                                ligne.getString("document_type"),
                                ligne.getBytes("document")))
                .optional();
    }

    /** Note qu'une réponse est attendue, sans toucher à ce qui a déjà été reçu pour ce jeu. */
    public void attendre(UUID dossier, Jeu jeu) {
        ecrire(
                dossier,
                Verification.sansReponse(jeu, EtatElement.A_TRAITER, horloge.instant()),
                null,
                "DO NOTHING");
    }

    /** Le registre n'a pas attesté (refus, personne inconnue, délai dépassé). */
    public void sansReponse(UUID dossier, Jeu jeu, EtatElement etat) {
        ecrire(dossier, Verification.sansReponse(jeu, etat, horloge.instant()), null, REMPLACER);
    }

    /** Garde le résultat d'une attestation vérifiée, à la place de ce qui était connu du jeu. */
    public void recevoir(UUID dossier, Jeu jeu, EtatElement etat, Attestation attestation) {
        Boolean valeur = null;
        Map<String, Object> champs = null;
        Verification.Piece piece = null;
        byte[] octets = null;
        switch (attestation.resultat()) {
            case Resultat.Statut statut -> valeur = statut.valeur();
            case Resultat.Champs recus -> champs = recus.champs();
            case Resultat.Document document -> {
                octets = document.contenu();
                piece = new Verification.Piece(document.nom(), document.type(), octets.length);
            }
        }
        ecrire(
                dossier,
                new Verification(
                        jeu.code(),
                        jeu.libelle(),
                        jeu.niveau(),
                        jeu.registre(),
                        etat,
                        attestation.id(),
                        valeur,
                        champs,
                        piece,
                        horloge.instant()),
                octets,
                REMPLACER);
    }

    /**
     * Dépose le dossier : ce que la personne a déclaré est figé. Rend faux si le dossier l'était
     * déjà ; les réponses des registres, elles, continuent d'arriver.
     */
    public boolean deposer(UUID dossier) {
        return base.sql(
                                "UPDATE dossier SET depose_le = ?, modifie_le = ?"
                                        + " WHERE id = ? AND depose_le IS NULL")
                        .params(maintenant(), maintenant(), dossier)
                        .update()
                == 1;
    }

    /** Recalcule l'état du dossier d'après ses vérifications. */
    public void actualiser(UUID dossier) {
        base.sql("UPDATE dossier SET etat = ?, modifie_le = ? WHERE id = ?")
                .params(EtatDossier.selon(verifications(dossier)).name(), maintenant(), dossier)
                .update();
    }

    private static final String REMPLACER =
            """
            DO UPDATE SET libelle = EXCLUDED.libelle, niveau = EXCLUDED.niveau,
                          registre = EXCLUDED.registre, etat = EXCLUDED.etat,
                          id_echange = EXCLUDED.id_echange, valeur = EXCLUDED.valeur,
                          champs = EXCLUDED.champs, document_nom = EXCLUDED.document_nom,
                          document_type = EXCLUDED.document_type, document = EXCLUDED.document,
                          modifiee_le = EXCLUDED.modifiee_le
            """;

    private void ecrire(UUID dossier, Verification v, byte[] octets, String siPresente) {
        base.sql(
                        """
                        INSERT INTO verification (dossier_id, jeu, libelle, niveau, registre, etat,
                                                  id_echange, valeur, champs, document_nom,
                                                  document_type, document, modifiee_le)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                        ON CONFLICT (dossier_id, jeu)
                        """
                                + siPresente)
                .params(
                        dossier,
                        v.jeu(),
                        v.libelle(),
                        v.niveau().name(),
                        v.registre(),
                        v.etat().name(),
                        v.idEchange(),
                        v.valeur(),
                        v.champs() == null ? null : json.writeValueAsString(v.champs()),
                        v.piece() == null ? null : v.piece().nom(),
                        v.piece() == null ? null : v.piece().type(),
                        octets,
                        OffsetDateTime.ofInstant(v.modifieeLe(), ZoneOffset.UTC))
                .update();
    }

    private Verification verification(ResultSet ligne, int numero) throws SQLException {
        String champs = ligne.getString("champs");
        String nomPiece = ligne.getString("document_nom");
        return new Verification(
                ligne.getString("jeu"),
                ligne.getString("libelle"),
                Niveau.valueOf(ligne.getString("niveau")),
                ligne.getString("registre"),
                EtatElement.valueOf(ligne.getString("etat")),
                ligne.getString("id_echange"),
                ligne.getObject("valeur", Boolean.class),
                champs == null ? null : json.readValue(champs, CHAMPS),
                nomPiece == null
                        ? null
                        : new Verification.Piece(
                                nomPiece,
                                ligne.getString("document_type"),
                                ligne.getLong("taille")),
                ligne.getTimestamp("modifiee_le").toInstant());
    }

    private static Dossier dossier(ResultSet ligne, int numero) throws SQLException {
        return new Dossier(
                ligne.getObject("id", UUID.class),
                ligne.getString("numero"),
                ligne.getString("pseudonyme"),
                EtatDossier.valueOf(ligne.getString("etat")),
                instant(ligne, "cree_le"),
                instant(ligne, "modifie_le"),
                ligne.getTimestamp("depose_le") == null ? null : instant(ligne, "depose_le"));
    }

    private static Instant instant(ResultSet ligne, String colonne) throws SQLException {
        return ligne.getTimestamp(colonne).toInstant();
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC);
    }
}
