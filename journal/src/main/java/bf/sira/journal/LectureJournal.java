package bf.sira.journal;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pages;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Sql;
import org.springframework.stereotype.Component;

/** Lit le journal, pour le citoyen (ses lignes) et pour l'administration (toutes). */
@Component
public class LectureJournal {

    private static final String FILTRES =
            " AND (CAST(:type AS text) IS NULL OR j.type = :type)"
                    + " AND (CAST(:service AS text) IS NULL OR j.service = :service)";

    private final Pages pages;
    private final CitoyensJournal citoyens;

    LectureJournal(Pages pages, CitoyensJournal citoyens) {
        this.pages = pages;
        this.citoyens = citoyens;
    }

    /** Les lignes qui concernent ce citoyen, la plus récente d'abord. */
    public Page<Ligne> duCitoyen(String citoyen, String type, String service, Pagination page) {
        return pages.lire(
                Ligne.COLONNES,
                Ligne.SOURCE + " WHERE j.citoyen = :citoyen" + FILTRES,
                "j.numero DESC",
                Sql.parametres(
                        "citoyen",
                        citoyens.pseudonymeJournal(citoyen),
                        "type",
                        Sql.filtre(type),
                        "service",
                        Sql.filtre(service)),
                page,
                (ligne, i) -> Ligne.lire(ligne, false));
    }

    public Page<Ligne> tout(String type, String service, String registre, Pagination page) {
        return pages.lire(
                Ligne.COLONNES,
                Ligne.SOURCE
                        + " WHERE (CAST(:registre AS text) IS NULL OR j.registre = :registre)"
                        + FILTRES,
                "j.numero DESC",
                Sql.parametres(
                        "type",
                        Sql.filtre(type),
                        "service",
                        Sql.filtre(service),
                        "registre",
                        Sql.filtre(registre)),
                page,
                (ligne, i) -> Ligne.lire(ligne, true));
    }
}
