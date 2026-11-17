package bf.sira.demo.service.dossiers;

import bf.sira.kit.EtatElement;
import bf.sira.kit.Jeu;
import bf.sira.kit.Niveau;
import java.time.Instant;
import java.util.Map;

/**
 * Une condition du dossier et ce que le registre en a attesté.
 *
 * @param idEchange identifiant de l'attestation reçue, gardé comme preuve
 * @param valeur réponse par oui ou par non (niveau {@code STATUT})
 * @param champs champs reçus (niveau {@code CHAMPS})
 * @param piece pièce reçue (niveau {@code DOCUMENT}) ; son contenu se lit à part
 */
public record Verification(
        String jeu,
        String libelle,
        Niveau niveau,
        String registre,
        EtatElement etat,
        String idEchange,
        Boolean valeur,
        Map<String, Object> champs,
        Piece piece,
        Instant modifieeLe) {

    public record Piece(String nom, String type, long taille) {}

    /** Une condition sans réponse : en attente, ou que le registre n'a pas pu attester. */
    static Verification sansReponse(Jeu jeu, EtatElement etat, Instant instant) {
        return new Verification(
                jeu.code(),
                jeu.libelle(),
                jeu.niveau(),
                jeu.registre(),
                etat,
                null,
                null,
                null,
                null,
                instant);
    }
}
