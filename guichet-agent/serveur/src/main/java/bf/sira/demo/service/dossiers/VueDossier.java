package bf.sira.demo.service.dossiers;

import bf.sira.kit.EtatElement;
import bf.sira.kit.Niveau;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un dossier tel que le voient le citoyen et l'agent : uniquement ce que le service a reçu. Le
 * pseudonyme n'y figure pas.
 *
 * @param deposeLe date du dépôt, absente tant que la personne n'a pas déposé son dossier
 * @param verifications ce que les registres ont attesté (« Vérifié par » le registre)
 * @param declarations ce que la personne a déclaré elle-même (« Déclaré par l'usager »)
 * @param piecesEvitees pièces que le citoyen n'a pas eu à fournir
 */
public record VueDossier(
        UUID id,
        String numero,
        EtatDossier etat,
        Instant creeLe,
        Instant modifieLe,
        Instant deposeLe,
        List<VueVerification> verifications,
        List<Declaration> declarations,
        List<String> piecesEvitees) {

    public record VueVerification(
            String jeu,
            String libelle,
            Niveau niveau,
            String registre,
            EtatElement etat,
            Boolean valeur,
            Map<String, Object> champs,
            Verification.Piece piece,
            Instant modifieeLe) {}

    static VueDossier de(
            Dossier dossier,
            List<Verification> verifications,
            List<Declaration> declarations,
            Map<String, String> pieces) {
        return new VueDossier(
                dossier.id(),
                dossier.numero(),
                dossier.etat(),
                dossier.creeLe(),
                dossier.modifieLe(),
                dossier.deposeLe(),
                verifications.stream()
                        .map(v ->
                                new VueVerification(
                                        v.jeu(),
                                        v.libelle(),
                                        v.niveau(),
                                        v.registre(),
                                        v.etat(),
                                        v.valeur(),
                                        v.champs(),
                                        v.piece(),
                                        v.modifieeLe()))
                        .toList(),
                declarations,
                verifications.stream()
                        .filter(v -> v.etat().porteUneAttestation())
                        .map(v -> pieces.get(v.registre()))
                        .filter(piece -> piece != null)
                        .distinct()
                        .toList());
    }
}
