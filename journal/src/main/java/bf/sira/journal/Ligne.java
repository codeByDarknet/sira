package bf.sira.journal;

import bf.sira.commun.donnees.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du journal telle qu'on la lit : les codes y sont accompagnés de leur nom. Elle ne porte
 * ni valeur de registre, ni identifiant unique.
 *
 * @param pseudonyme début du pseudonyme de journal du citoyen, pour l'administration seulement
 * @param signalee le citoyen a contesté cet accès
 */
public record Ligne(
        UUID id,
        long numero,
        Instant horodatage,
        String type,
        String resultat,
        String pseudonyme,
        Reference service,
        Reference registre,
        JeuCite jeu,
        String niveau,
        DemarcheCitee demarche,
        String finalite,
        String canal,
        String idEchange,
        String detail,
        String acteur,
        String objet,
        boolean signalee) {

    public record Reference(String code, String nom) {}

    public record JeuCite(String code, String libelle, String niveau) {}

    public record DemarcheCitee(String code, String libelle) {}

    static final String COLONNES =
            "j.id, j.numero, j.horodatage, j.type, j.resultat, j.citoyen, j.service,"
                    + " m.nom AS service_nom, j.registre, r.nom AS registre_nom, j.jeu,"
                    + " je.libelle AS jeu_libelle, j.niveau, j.demarche,"
                    + " d.libelle AS demarche_libelle, j.finalite, j.canal, j.id_echange,"
                    + " j.detail, j.acteur, j.objet, s.id IS NOT NULL AS signalee";

    static final String SOURCE =
            "journal j LEFT JOIN membre m ON m.code = j.service"
                    + " LEFT JOIN registre r ON r.code = j.registre"
                    + " LEFT JOIN jeu je ON je.code = j.jeu"
                    + " LEFT JOIN demarche d ON d.code = j.demarche"
                    + " LEFT JOIN signalement s ON s.ligne = j.id";

    /** Assez pour rapprocher deux lignes d'une même personne, trop peu pour la désigner. */
    private static final int TAILLE_PSEUDONYME = 8;

    /**
     * @param pourAdministration vrai pour montrer le pseudonyme tronqué et l'acteur ; le citoyen,
     *     lui, ne lit que ses propres lignes
     */
    static Ligne lire(ResultSet ligne, boolean pourAdministration) throws SQLException {
        String citoyen = ligne.getString("citoyen");
        String niveau = ligne.getString("niveau");
        return new Ligne(
                ligne.getObject("id", UUID.class),
                ligne.getLong("numero"),
                Sql.instant(ligne, "horodatage"),
                ligne.getString("type"),
                ligne.getString("resultat"),
                pourAdministration && citoyen != null
                        ? citoyen.substring(0, Math.min(TAILLE_PSEUDONYME, citoyen.length()))
                        : null,
                reference(ligne.getString("service"), ligne.getString("service_nom")),
                reference(ligne.getString("registre"), ligne.getString("registre_nom")),
                ligne.getString("jeu") == null
                        ? null
                        : new JeuCite(
                                ligne.getString("jeu"), ligne.getString("jeu_libelle"), niveau),
                niveau,
                ligne.getString("demarche") == null
                        ? null
                        : new DemarcheCitee(
                                ligne.getString("demarche"), ligne.getString("demarche_libelle")),
                ligne.getString("finalite"),
                ligne.getString("canal"),
                ligne.getString("id_echange"),
                ligne.getString("detail"),
                pourAdministration ? ligne.getString("acteur") : null,
                ligne.getString("objet"),
                ligne.getBoolean("signalee"));
    }

    private static Reference reference(String code, String nom) {
        return code == null ? null : new Reference(code, nom == null ? code : nom);
    }
}
