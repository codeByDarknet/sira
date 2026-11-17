package bf.sira.demo.registre.personnes;

import bf.sira.demo.registre.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class DepotPersonnes {

    private static final TypeReference<Map<String, Object>> FICHE = new TypeReference<>() {};

    private final JdbcClient base;
    private final JsonMapper json;

    DepotPersonnes(JdbcClient base, JsonMapper json) {
        this.base = base;
        this.json = json;
    }

    public Optional<Personne> lire(String identifiant) {
        if (!Personne.identifiantPlausible(identifiant)) {
            return Optional.empty();
        }
        return base.sql(
                        "SELECT id, identifiant, fiche, modifiee_le FROM personne WHERE identifiant = ?")
                .param(identifiant)
                .query(this::personne)
                .optional();
    }

    /** La fiche désignée par son identifiant technique (adresses du back-office). */
    public Optional<Personne> lireParId(UUID id) {
        return base.sql("SELECT id, identifiant, fiche, modifiee_le FROM personne WHERE id = ?")
                .param(id)
                .query(this::personne)
                .optional();
    }

    Page<Personne> lister(String recherche, int page, int taille) {
        String motif = "%" + echapper(normaliser(recherche == null ? "" : recherche)) + "%";
        long total =
                base.sql("SELECT count(*) FROM personne WHERE recherche LIKE ? ESCAPE '\\'")
                        .param(motif)
                        .query(Long.class)
                        .single();
        List<Personne> elements =
                base.sql(
                                """
                                SELECT id, identifiant, fiche, modifiee_le FROM personne
                                 WHERE recherche LIKE ? ESCAPE '\\'
                                 ORDER BY creee_le DESC, fiche ->> 'nom', fiche ->> 'prenoms',
                                          identifiant
                                 LIMIT ? OFFSET ?
                                """)
                        .params(motif, taille, Page.decalage(page, taille))
                        .query(this::personne)
                        .list();
        return new Page<>(elements, page, taille, total);
    }

    /** Ajoute la personne si elle est absente ; une fiche déjà corrigée par un agent est gardée. */
    boolean ajouter(String identifiant, Map<String, Object> fiche) {
        return base.sql(
                                """
                                INSERT INTO personne (identifiant, fiche, recherche)
                                VALUES (?, CAST(? AS jsonb), ?)
                                ON CONFLICT (identifiant) DO NOTHING
                                """)
                        .params(identifiant, json.writeValueAsString(fiche), recherche(fiche))
                        .update()
                == 1;
    }

    /** Retire la personne du registre (et ses pièces) ; faux si elle n'existait pas. */
    boolean supprimer(UUID id) {
        return base.sql("DELETE FROM personne WHERE id = ?").param(id).update() == 1;
    }

    /** Fusionne les champs corrigés dans la fiche ; rend la personne à jour, si elle existe. */
    Optional<Personne> corriger(String identifiant, Map<String, Object> champs) {
        return lire(identifiant)
                .map(personne -> {
                    Map<String, Object> fiche = new LinkedHashMap<>(personne.fiche());
                    fiche.putAll(champs);
                    return base.sql(
                                    """
                                    UPDATE personne
                                       SET fiche = CAST(? AS jsonb), recherche = ?, modifiee_le = now()
                                     WHERE identifiant = ?
                                    RETURNING id, identifiant, fiche, modifiee_le
                                    """)
                            .params(json.writeValueAsString(fiche), recherche(fiche), identifiant)
                            .query(this::personne)
                            .single();
                });
    }

    private Personne personne(ResultSet ligne, int numero) throws SQLException {
        return new Personne(
                ligne.getObject("id", UUID.class),
                ligne.getString("identifiant"),
                json.readValue(ligne.getString("fiche"), FICHE),
                ligne.getTimestamp("modifiee_le").toInstant());
    }

    private static String recherche(Map<String, Object> fiche) {
        return normaliser(
                fiche.get(Personne.NOM)
                        + " "
                        + fiche.get(Personne.PRENOMS)
                        + " "
                        + fiche.get(Personne.IDENTIFIANT));
    }

    /** Sans accents ni majuscules : « kabore » trouve « KABORÉ ». */
    static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Dans un motif LIKE, les caractères jokers saisis par l'agent restent de simples caractères.
     */
    private static String echapper(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
