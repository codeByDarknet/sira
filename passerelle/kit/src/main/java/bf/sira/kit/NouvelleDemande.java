package bf.sira.kit;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ce qu'un service indique pour ouvrir une demande. Les jeux ne se choisissent pas ici : ce sont
 * ceux de l'habilitation approuvée pour la démarche.
 *
 * @param retourUrl où renvoyer le navigateur après l'accord (canal {@code REDIRECTION})
 * @param identifiantUnique identifiant unique du citoyen : obligatoire (ou pseudonyme) sur {@code
 *     NOTIFICATION} ; facultatif sur {@code REDIRECTION} et {@code CODE}, où il désigne la seule
 *     personne autorisée à donner l'accord (celui que l'usager a saisi lui-même)
 * @param pseudonyme pseudonyme déjà connu du service (canal {@code NOTIFICATION})
 */
public record NouvelleDemande(
        String demarche,
        Canal canal,
        @JsonProperty("retour_url") String retourUrl,
        @JsonProperty("identifiant_unique") String identifiantUnique,
        String pseudonyme) {

    public static NouvelleDemande parRedirection(String demarche, String retourUrl) {
        return new NouvelleDemande(demarche, Canal.REDIRECTION, retourUrl, null, null);
    }

    /**
     * Redirection avec désignation : seul le citoyen dont l'identifiant est donné pourra accepter.
     * {@code identifiantUnique} peut être {@code null} (aucune désignation).
     */
    public static NouvelleDemande parRedirection(
            String demarche, String retourUrl, String identifiantUnique) {
        return new NouvelleDemande(demarche, Canal.REDIRECTION, retourUrl, identifiantUnique, null);
    }

    public static NouvelleDemande parCode(String demarche) {
        return new NouvelleDemande(demarche, Canal.CODE, null, null, null);
    }

    /**
     * Code court avec désignation : seul le citoyen dont l'identifiant est donné pourra accepter.
     * {@code identifiantUnique} peut être {@code null} (aucune désignation).
     */
    public static NouvelleDemande parCode(String demarche, String identifiantUnique) {
        return new NouvelleDemande(demarche, Canal.CODE, null, identifiantUnique, null);
    }

    public static NouvelleDemande parNotification(String demarche, String identifiantUnique) {
        return new NouvelleDemande(demarche, Canal.NOTIFICATION, null, identifiantUnique, null);
    }

    public static NouvelleDemande parPseudonyme(String demarche, String pseudonyme) {
        return new NouvelleDemande(demarche, Canal.NOTIFICATION, null, null, pseudonyme);
    }
}
