package bf.sira.demo.service.demandes;

import bf.sira.kit.Canal;
import bf.sira.kit.EtatDemandeCode;
import bf.sira.kit.Jeu;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class DepotDemandes {

    private static final TypeReference<List<Jeu>> JEUX = new TypeReference<>() {};
    private static final String COLONNES =
            "id, canal, origine, etat, jeux, code_retour, dossier_id, creee_le, expire_le";

    private final JdbcClient base;
    private final JsonMapper json;

    DepotDemandes(JdbcClient base, JsonMapper json) {
        this.base = base;
        this.json = json;
    }

    void ajouter(DemandeSuivie demande) {
        base.sql(
                        "INSERT INTO demande ("
                                + COLONNES
                                + ") VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)")
                .params(
                        demande.id(),
                        demande.canal().name(),
                        demande.origine().name(),
                        demande.etat().name(),
                        json.writeValueAsString(demande.jeux()),
                        demande.codeRetour(),
                        demande.dossierId(),
                        horodatage(demande.creeeLe()),
                        horodatage(demande.expireLe()))
                .update();
    }

    public Optional<DemandeSuivie> lire(UUID id) {
        return base.sql("SELECT " + COLONNES + " FROM demande WHERE id = ?")
                .param(id)
                .query(this::demande)
                .optional();
    }

    public List<DemandeSuivie> duDossier(UUID dossier) {
        return base.sql(
                        "SELECT "
                                + COLONNES
                                + " FROM demande WHERE dossier_id = ? ORDER BY creee_le DESC LIMIT 20")
                .param(dossier)
                .query(this::demande)
                .list();
    }

    /**
     * Les demandes que personne d'autre ne suit : celles dont un registre n'a pas encore répondu,
     * et celles qu'un agent a envoyées (aucun écran n'attend la réponse du citoyen).
     */
    List<DemandeSuivie> aRelancer(int nombre) {
        return base.sql(
                        "SELECT "
                                + COLONNES
                                + """
                                 FROM demande
                                 WHERE etat IN ('EN_COURS', 'PARTIELLE')
                                    OR (etat = 'EN_ATTENTE_ACCORD' AND origine = 'AGENT')
                                 ORDER BY creee_le LIMIT ?
                                """)
                .param(nombre)
                .query(this::demande)
                .list();
    }

    void noter(UUID id, EtatDemandeCode etat, UUID dossier, String codeRetour) {
        base.sql("UPDATE demande SET etat = ?, dossier_id = ?, code_retour = ? WHERE id = ?")
                .params(etat.name(), dossier, codeRetour, id)
                .update();
    }

    /** Les demandes laissées sans accord au-delà de leur durée de vie n'aboutiront plus. */
    void expirer(Instant limite) {
        base.sql(
                        """
                        UPDATE demande SET etat = 'EXPIREE'
                         WHERE etat = 'EN_ATTENTE_ACCORD' AND origine = 'CITOYEN' AND expire_le < ?
                        """)
                .param(horodatage(limite))
                .update();
    }

    /** Une demande restée sans dossier ne désigne personne : rien ne justifie de la garder. */
    void purger(Instant limite) {
        base.sql("DELETE FROM demande WHERE dossier_id IS NULL AND expire_le < ?")
                .param(horodatage(limite))
                .update();
    }

    private DemandeSuivie demande(ResultSet ligne, int numero) throws SQLException {
        return new DemandeSuivie(
                ligne.getObject("id", UUID.class),
                Canal.valueOf(ligne.getString("canal")),
                Origine.valueOf(ligne.getString("origine")),
                EtatDemandeCode.valueOf(ligne.getString("etat")),
                json.readValue(ligne.getString("jeux"), JEUX),
                ligne.getString("code_retour"),
                ligne.getObject("dossier_id", UUID.class),
                ligne.getTimestamp("creee_le").toInstant(),
                ligne.getTimestamp("expire_le").toInstant());
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
