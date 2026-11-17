package bf.sira.passerelle.collecte;

import bf.sira.commun.donnees.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Un élément à obtenir, avec tout ce qu'il faut pour le demander : pour qui, au nom de quel accord,
 * à quel registre.
 *
 * @param citoyen pseudonyme interne du citoyen
 * @param certificat certificat de chiffrement du service, en PEM
 * @param canal canal par lequel l'accord a été donné
 */
record Tache(
        UUID element,
        UUID demande,
        Instant creeLe,
        int tentatives,
        String service,
        String citoyen,
        String certificat,
        String demarche,
        String finalite,
        String canal,
        UUID autorisation,
        Instant accordeeLe,
        String registre,
        String adresse,
        String transport,
        String jeu) {

    static final String SELECTION =
            "SELECT e.id, e.demande, e.cree_le, e.tentatives, d.service, d.citoyen,"
                    + " d.certificat_chiffrement, d.demarche, m.finalite, a.canal, a.id AS autorisation,"
                    + " a.accordee_le, e.registre, r.adresse, r.transport, e.jeu"
                    + " FROM element e"
                    + " JOIN demande d ON d.id = e.demande"
                    + " JOIN demarche m ON m.code = d.demarche"
                    + " JOIN autorisation a ON a.id = d.autorisation"
                    + " JOIN registre r ON r.code = e.registre"
                    + " WHERE e.etat = 'A_TRAITER'";

    static Tache lire(ResultSet ligne, int rang) throws SQLException {
        return new Tache(
                ligne.getObject("id", UUID.class),
                ligne.getObject("demande", UUID.class),
                Sql.instant(ligne, "cree_le"),
                ligne.getInt("tentatives"),
                ligne.getString("service"),
                ligne.getString("citoyen"),
                ligne.getString("certificat_chiffrement"),
                ligne.getString("demarche"),
                ligne.getString("finalite"),
                ligne.getString("canal"),
                ligne.getObject("autorisation", UUID.class),
                Sql.instant(ligne, "accordee_le"),
                ligne.getString("registre"),
                ligne.getString("adresse"),
                ligne.getString("transport"),
                ligne.getString("jeu"));
    }
}
