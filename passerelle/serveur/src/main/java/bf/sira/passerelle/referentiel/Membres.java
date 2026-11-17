package bf.sira.passerelle.referentiel;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Recherche;
import bf.sira.commun.donnees.Sql;
import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.SaisieRefusee;
import bf.sira.commun.jetons.Acteur;
import bf.sira.journal.Journal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Les membres du réseau. */
@Component
public class Membres extends Catalogue<MembreDeclare> {

    Membres(JdbcClient sql, Pages pages, Journal journal) {
        super(sql, pages, journal, "membre", "membre", (ligne, i) -> lire(ligne));
    }

    public Page<MembreDeclare> lister(Pagination pagination, Recherche recherche) {
        return lister(
                null,
                Map.of(),
                pagination,
                recherche,
                List.of("code", "nom", "organisme"),
                List.of("code", "nom", "organisme", "actif"));
    }

    @Override
    protected String code(MembreDeclare membre) {
        return membre.code();
    }

    @Override
    protected void verifier(MembreDeclare membre) {
        verifierRetours(membre.retoursAutorises());
    }

    /**
     * Une adresse de retour reçoit le citoyen juste après son accord : la changer est une décision
     * de l'autorité ({@link #changerRetours}), jamais un simple remplacement.
     */
    @Override
    protected void verifierRemplacement(MembreDeclare actuel, MembreDeclare remplacant) {
        if (!Set.copyOf(actuel.retoursAutorises())
                .equals(Set.copyOf(remplacant.retoursAutorises()))) {
            throw ErreurMetier.conflit(
                    "Les adresses de retour se changent par une décision de l'autorité.");
        }
    }

    /** Décision de l'autorité : remplace les adresses de retour du membre. */
    @Transactional
    public MembreDeclare changerRetours(String code, List<String> retours, Acteur acteur) {
        verifierRetours(retours);
        int touches =
                sql.sql("UPDATE membre SET retours_autorises = ? WHERE code = ?")
                        .params(Sql.tableau(retours), code)
                        .update();
        return decide(code, touches, "MODIFICATION_RETOURS", acteur);
    }

    private static void verifierRetours(List<String> retours) {
        if (!retours.stream().allMatch(Membres::estAdresseDeRetour)) {
            throw new SaisieRefusee(
                    "retours_autorises",
                    "Une adresse de retour est une adresse http(s) complète, sans fragment.");
        }
    }

    @Override
    protected void inserer(MembreDeclare membre) {
        sql.sql(
                        "INSERT INTO membre (code, nom, organisme, est_service, est_registre,"
                                + " retours_autorises, actif) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(
                        membre.code(),
                        membre.nom(),
                        membre.organisme(),
                        membre.roles().contains(RoleMembre.SERVICE),
                        membre.roles().contains(RoleMembre.REGISTRE),
                        Sql.tableau(membre.retoursAutorises()),
                        membre.actif())
                .update();
    }

    @Override
    protected int remplacer(MembreDeclare membre) {
        return sql.sql(
                        "UPDATE membre SET nom = ?, organisme = ?, est_service = ?,"
                                + " est_registre = ?, retours_autorises = ?, actif = ?"
                                + " WHERE code = ?")
                .params(
                        membre.nom(),
                        membre.organisme(),
                        membre.roles().contains(RoleMembre.SERVICE),
                        membre.roles().contains(RoleMembre.REGISTRE),
                        Sql.tableau(membre.retoursAutorises()),
                        membre.actif(),
                        membre.code())
                .update();
    }

    private static boolean estAdresseDeRetour(String adresse) {
        try {
            URI uri = new URI(adresse);
            return uri.isAbsolute()
                    && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static MembreDeclare lire(ResultSet ligne) throws SQLException {
        Set<RoleMembre> roles = EnumSet.noneOf(RoleMembre.class);
        if (ligne.getBoolean("est_service")) {
            roles.add(RoleMembre.SERVICE);
        }
        if (ligne.getBoolean("est_registre")) {
            roles.add(RoleMembre.REGISTRE);
        }
        return new MembreDeclare(
                ligne.getString("code"),
                ligne.getString("nom"),
                ligne.getString("organisme"),
                roles,
                Sql.liste(ligne, "retours_autorises"),
                ligne.getBoolean("actif"));
    }
}
