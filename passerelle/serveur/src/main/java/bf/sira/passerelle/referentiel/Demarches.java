package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.journal.Journal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Les démarches pour lesquelles des services demandent des informations. */
@Component
public class Demarches extends Catalogue<Demarche> {

    Demarches(JdbcClient sql, Pages pages, Journal journal) {
        super(sql, pages, journal, "demarche", "demarche", (ligne, i) -> lire(ligne));
    }

    public Page<Demarche> lister(Pagination pagination, Recherche recherche) {
        return lister(
                null,
                Map.of(),
                pagination,
                recherche,
                List.of("code", "libelle", "service"),
                List.of("code", "libelle", "service", "duree_autorisation_jours", "actif"));
    }

    @Override
    protected String code(Demarche demarche) {
        return demarche.code();
    }

    @Override
    protected void verifier(Demarche demarche) {
        if (!existe("SELECT FROM membre WHERE code = ? AND est_service", demarche.service())) {
            throw new SaisieRefusee(
                    "service", "Ce membre n'existe pas, ou ne demande pas d'informations.");
        }
    }

    @Override
    protected void inserer(Demarche demarche) {
        sql.sql(
                        "INSERT INTO demarche (code, libelle, finalite, base_legale, service,"
                                + " duree_autorisation_jours, actif) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(
                        demarche.code(),
                        demarche.libelle(),
                        demarche.finalite(),
                        demarche.baseLegale(),
                        demarche.service(),
                        demarche.dureeAutorisationJours(),
                        demarche.actif())
                .update();
    }

    @Override
    protected int remplacer(Demarche demarche) {
        return sql.sql(
                        "UPDATE demarche SET libelle = ?, finalite = ?, base_legale = ?,"
                                + " service = ?, duree_autorisation_jours = ?, actif = ?"
                                + " WHERE code = ?")
                .params(
                        demarche.libelle(),
                        demarche.finalite(),
                        demarche.baseLegale(),
                        demarche.service(),
                        demarche.dureeAutorisationJours(),
                        demarche.actif(),
                        demarche.code())
                .update();
    }

    private static Demarche lire(ResultSet ligne) throws SQLException {
        return new Demarche(
                ligne.getString("code"),
                ligne.getString("libelle"),
                ligne.getString("finalite"),
                ligne.getString("base_legale"),
                ligne.getString("service"),
                ligne.getInt("duree_autorisation_jours"),
                ligne.getBoolean("actif"));
    }
}
