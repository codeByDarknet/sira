package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.journal.Journal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les jeux de données que les registres acceptent de fournir. */
@Component
public class Jeux extends Catalogue<Jeu> {

    /** Au-delà, une réponse ne dit plus grand-chose de la situation de la personne. */
    private static final Duration VALIDITE_MAX = Duration.ofDays(366);

    private static final int TAILLE_VALEUR_MAX = 200;

    private final JsonMapper json;

    Jeux(JdbcClient sql, Pages pages, Journal journal, JsonMapper json) {
        super(sql, pages, journal, "jeu", "jeu", (ligne, i) -> lire(ligne, json));
        this.json = json;
    }

    public Page<Jeu> lister(
            String registre, String niveau, Pagination pagination, Recherche recherche) {
        return lister(
                "(CAST(:registre AS text) IS NULL OR registre = :registre)"
                        + " AND (CAST(:niveau AS text) IS NULL OR niveau = :niveau)",
                Sql.parametres("registre", Sql.filtre(registre), "niveau", Sql.filtre(niveau)),
                pagination,
                recherche,
                List.of("code", "libelle", "registre"),
                List.of("code", "libelle", "registre", "niveau", "actif"));
    }

    /** Les jeux nommés, dans l'ordre de leur code. */
    public List<Jeu> parCodes(List<String> codes) {
        return sql.sql("SELECT * FROM jeu WHERE code = ANY (?) ORDER BY code")
                .param(Sql.tableau(codes))
                .query((ligne, i) -> lire(ligne, json))
                .list();
    }

    /** La durée pendant laquelle une réponse à ce jeu reste réutilisable. */
    public static Duration validite(Jeu jeu) {
        return Duration.parse(jeu.validite());
    }

    @Override
    protected String code(Jeu jeu) {
        return jeu.code();
    }

    @Override
    protected void verifier(Jeu jeu) {
        verifierForme(jeu);
        if (!existe("SELECT FROM registre WHERE code = ?", jeu.registre())) {
            throw new SaisieRefusee("registre", "Ce registre n'existe pas.");
        }
    }

    /**
     * Un jeu qu'une habilitation soumise ou décidée, ou une autorisation non retirée, a couvert ne
     * change plus que d'état actif : sinon l'instructeur seul élargirait ce que l'autorité et les
     * citoyens ont approuvé. Une autre question demande un nouveau code de jeu.
     */
    @Override
    protected void verifierRemplacement(Jeu actuel, Jeu remplacant) {
        boolean meme = actuel.equals(avecActif(remplacant, actuel.actif()));
        if (!meme
                && existe(
                        "SELECT FROM habilitation_jeu hj JOIN habilitation h ON h.id ="
                                + " hj.habilitation WHERE hj.jeu = ? AND h.etat IN ('SOUMISE',"
                                + " 'APPROUVEE', 'SUSPENDUE') UNION ALL SELECT FROM autorisation"
                                + " WHERE ? = ANY (jeux) AND retiree_le IS NULL",
                        actuel.code(),
                        actuel.code())) {
            throw ErreurMetier.conflit(
                    "Ce jeu est couvert par une habilitation : créez un nouveau jeu.");
        }
    }

    private static Jeu avecActif(Jeu jeu, boolean actif) {
        return new Jeu(
                jeu.code(),
                jeu.registre(),
                jeu.niveau(),
                jeu.libelle(),
                jeu.description(),
                jeu.validite(),
                jeu.regle(),
                jeu.champs(),
                jeu.document(),
                actif);
    }

    /** Ce qu'un jeu doit respecter avant même d'être rapproché du référentiel. */
    public static void verifierForme(Jeu jeu) {
        try {
            Duration validite = validite(jeu);
            if (validite.isNegative()
                    || validite.isZero()
                    || validite.compareTo(VALIDITE_MAX) > 0) {
                throw new SaisieRefusee("validite", "La validité va d'un instant à 366 jours.");
            }
        } catch (DateTimeParseException e) {
            throw new SaisieRefusee("validite", "Écrivez une durée ISO 8601, par exemple P90D.");
        }
        boolean coherent =
                switch (jeu.niveau()) {
                    case STATUT -> jeu.regle() != null && sans(jeu.champs(), jeu.document());
                    case CHAMPS -> jeu.champs() != null && sans(jeu.regle(), jeu.document());
                    case DOCUMENT -> jeu.document() != null && sans(jeu.regle(), jeu.champs());
                };
        if (!coherent) {
            throw new SaisieRefusee(
                    "niveau",
                    "Un jeu porte une règle (STATUT), des champs (CHAMPS) ou un document"
                            + " (DOCUMENT), et rien d'autre.");
        }
        JsonNode valeur = jeu.regle() == null ? null : jeu.regle().valeur();
        if (valeur != null
                && !valeur.isNull()
                && (!valeur.isValueNode() || valeur.asString().length() > TAILLE_VALEUR_MAX)) {
            throw new SaisieRefusee(
                    "regle.valeur", "La valeur d'une règle est un texte ou un nombre.");
        }
    }

    @Override
    protected void inserer(Jeu jeu) {
        sql.sql(
                        "INSERT INTO jeu (code, registre, niveau, libelle, description, validite,"
                                + " regle, champs, document, actif)"
                                + " VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)")
                .params(
                        jeu.code(),
                        jeu.registre(),
                        jeu.niveau().name(),
                        jeu.libelle(),
                        jeu.description(),
                        jeu.validite(),
                        regle(jeu),
                        Sql.tableau(jeu.champs()),
                        jeu.document(),
                        jeu.actif())
                .update();
    }

    @Override
    protected int remplacer(Jeu jeu) {
        return sql.sql(
                        "UPDATE jeu SET registre = ?, niveau = ?, libelle = ?, description = ?,"
                                + " validite = ?, regle = CAST(? AS jsonb), champs = ?,"
                                + " document = ?, actif = ? WHERE code = ?")
                .params(
                        jeu.registre(),
                        jeu.niveau().name(),
                        jeu.libelle(),
                        jeu.description(),
                        jeu.validite(),
                        regle(jeu),
                        Sql.tableau(jeu.champs()),
                        jeu.document(),
                        jeu.actif(),
                        jeu.code())
                .update();
    }

    private String regle(Jeu jeu) {
        return jeu.regle() == null ? null : json.writeValueAsString(jeu.regle());
    }

    private static boolean sans(Object premier, Object second) {
        return premier == null && second == null;
    }

    private static Jeu lire(ResultSet ligne, JsonMapper json) throws SQLException {
        String regle = ligne.getString("regle");
        return new Jeu(
                ligne.getString("code"),
                ligne.getString("registre"),
                Niveau.valueOf(ligne.getString("niveau")),
                ligne.getString("libelle"),
                ligne.getString("description"),
                ligne.getString("validite"),
                regle == null ? null : json.readValue(regle, Jeu.Regle.class),
                Sql.liste(ligne, "champs"),
                ligne.getString("document"),
                ligne.getBoolean("actif"));
    }
}
