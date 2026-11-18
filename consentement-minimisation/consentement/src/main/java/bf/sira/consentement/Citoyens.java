package bf.sira.consentement;

import bf.sira.commun.crypto.Pseudonymes;
import bf.sira.journal.CitoyensJournal;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Les citoyens connus de la passerelle. L'identifiant unique n'est conservé que dans la table
 * {@code citoyen} ; il n'en sort que vers les connecteurs. Partout ailleurs circule un pseudonyme :
 * interne (clé des tables), propre à un service, ou propre au journal (contrat §5.9).
 */
@Component
public class Citoyens implements CitoyensJournal {

    /** Forme d'un identifiant unique : opaque, sans autre hypothèse sur son format réel. */
    public static final String FORME_IDENTIFIANT = "[A-Za-z0-9]{6,32}";

    private static final Pattern IDENTIFIANT = Pattern.compile(FORME_IDENTIFIANT);
    private static final String SECTEUR_INTERNE = "passerelle";
    private static final String SECTEUR_JOURNAL = "journal";
    private static final String REVENDICATION = "iue";

    private final JdbcClient sql;
    private final Pseudonymes pseudonymes;

    Citoyens(JdbcClient sql, Pseudonymes pseudonymes) {
        this.sql = sql;
        this.pseudonymes = pseudonymes;
    }

    /**
     * Le citoyen qui porte ce jeton. Son identifiant vient de la revendication {@code iue}, jamais
     * d'un paramètre (contrat §7.2).
     */
    @Override
    public String courant(Jwt jeton) {
        String identifiant = jeton.getClaimAsString(REVENDICATION);
        if (identifiant == null || !IDENTIFIANT.matcher(identifiant).matches()) {
            throw new AccessDeniedException("jeton sans identifiant unique");
        }
        return enregistrer(identifiant);
    }

    /**
     * @return le pseudonyme interne du citoyen
     */
    public String enregistrer(String identifiantUnique) {
        String id = designation(identifiantUnique);
        sql.sql(
                        "INSERT INTO citoyen (id, identifiant_unique) VALUES (?, ?)"
                                + " ON CONFLICT DO NOTHING")
                .params(id, identifiantUnique)
                .update();
        return id;
    }

    /**
     * Le pseudonyme interne qu'aurait la personne de cet identifiant, sans l'enregistrer : un
     * identifiant tapé par un tiers ne crée aucun citoyen.
     */
    public String designation(String identifiantUnique) {
        return pseudonymes.calculer(SECTEUR_INTERNE, identifiantUnique);
    }

    public String identifiantUnique(String citoyen) {
        return sql.sql("SELECT identifiant_unique FROM citoyen WHERE id = ?")
                .param(citoyen)
                .query(String.class)
                .single();
    }

    /**
     * Le pseudonyme du citoyen chez ce service, mémorisé pour que le service puisse le rappeler.
     */
    public String pseudonymeChez(String service, String citoyen) {
        String pseudonyme = pseudonymes.calculer("service:" + service, identifiantUnique(citoyen));
        sql.sql(
                        "INSERT INTO pseudonyme_service (service, pseudonyme, citoyen)"
                                + " VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(service, pseudonyme, citoyen)
                .update();
        return pseudonyme;
    }

    /** Le citoyen qu'un service désigne par le pseudonyme qu'il a déjà reçu. */
    public Optional<String> parPseudonyme(String service, String pseudonyme) {
        return sql.sql(
                        "SELECT citoyen FROM pseudonyme_service WHERE service = ? AND pseudonyme = ?")
                .params(service, pseudonyme)
                .query(String.class)
                .optional();
    }

    /** La désignation du citoyen dans le journal. */
    @Override
    public String pseudonymeJournal(String citoyen) {
        return pseudonymes.calculer(SECTEUR_JOURNAL, identifiantUnique(citoyen));
    }
}
