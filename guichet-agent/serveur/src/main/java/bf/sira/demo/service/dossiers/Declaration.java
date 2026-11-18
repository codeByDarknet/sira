package bf.sira.demo.service.dossiers;

import bf.sira.demo.service.configuration.ProprietesFormulaire.Nature;
import java.time.Instant;

/**
 * Une information que la personne a déclarée elle-même : jamais vérifiée par un registre.
 *
 * @param remplace vrai quand la pièce remplace le jeu de registre de même code, non transmis
 * @param valeur la saisie (nature {@code TEXTE})
 * @param piece la pièce jointe (nature {@code PIECE}) ; son contenu se lit à part
 */
public record Declaration(
        String code,
        String libelle,
        Nature nature,
        boolean remplace,
        String valeur,
        Verification.Piece piece,
        Instant modifieeLe) {}
