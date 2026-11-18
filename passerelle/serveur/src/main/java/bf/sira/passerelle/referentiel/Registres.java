package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.jetons.Acteur;
import bf.sira.journal.Journal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Les registres et l'adresse de leur connecteur. */
@Component
public class Registres extends Catalogue<Registre> {

    Registres(JdbcClient sql, Pages pages, Journal journal) {
        super(sql, pages, journal, "registre", "registre", (ligne, i) -> lire(ligne));
    }

    public Page<Registre> lister(Pagination pagination, Recherche recherche) {
        return lister(
                null,
                Map.of(),
                pagination,
                recherche,
                List.of("code", "nom", "membre"),
                List.of("code", "nom", "membre", "transport", "actif"));
    }

    public List<Registre> actifs() {
        return sql.sql("SELECT * FROM registre WHERE actif ORDER BY code")
                .query((ligne, i) -> lire(ligne))
                .list();
    }

    @Override
    protected String code(Registre registre) {
        return registre.code();
    }

    @Override
    protected void verifier(Registre registre) {
        if (!existe("SELECT FROM membre WHERE code = ? AND est_registre", registre.membre())) {
            throw new SaisieRefusee(
                    "membre", "Ce membre n'existe pas, ou ne fournit pas d'informations.");
        }
    }

    /**
     * L'adresse du connecteur reçoit les questions sur les personnes : la changer est une décision
     * de l'autorité ({@link #changerAdresse}), jamais un simple remplacement.
     */
    @Override
    protected void verifierRemplacement(Registre actuel, Registre remplacant) {
        if (!actuel.adresse().equals(remplacant.adresse())) {
            throw ErreurMetier.conflit(
                    "L'adresse d'un registre se change par une décision de l'autorité.");
        }
    }

    /** Décision de l'autorité : change l'adresse du connecteur du registre. */
    @Transactional
    public Registre changerAdresse(String code, String adresse, Acteur acteur) {
        int touches =
                sql.sql("UPDATE registre SET adresse = ? WHERE code = ?")
                        .params(adresse, code)
                        .update();
        return decide(code, touches, "MODIFICATION_ADRESSE", acteur);
    }

    @Override
    protected void inserer(Registre registre) {
        sql.sql(
                        "INSERT INTO registre (code, nom, membre, adresse, transport, actif)"
                                + " VALUES (?, ?, ?, ?, ?, ?)")
                .params(
                        registre.code(),
                        registre.nom(),
                        registre.membre(),
                        registre.adresse(),
                        registre.transport(),
                        registre.actif())
                .update();
    }

    @Override
    protected int remplacer(Registre registre) {
        return sql.sql(
                        "UPDATE registre SET nom = ?, membre = ?, adresse = ?, transport = ?,"
                                + " actif = ? WHERE code = ?")
                .params(
                        registre.nom(),
                        registre.membre(),
                        registre.adresse(),
                        registre.transport(),
                        registre.actif(),
                        registre.code())
                .update();
    }

    private static Registre lire(ResultSet ligne) throws SQLException {
        return new Registre(
                ligne.getString("code"),
                ligne.getString("nom"),
                ligne.getString("membre"),
                ligne.getString("adresse"),
                ligne.getString("transport"),
                ligne.getBoolean("actif"));
    }
}
