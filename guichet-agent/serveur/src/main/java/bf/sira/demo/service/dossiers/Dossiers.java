package bf.sira.demo.service.dossiers;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.service.configuration.ProprietesService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** Lecture d'un dossier, commune au citoyen et à l'agent. */
@Service
public class Dossiers {

    private final DepotDossiers depot;
    private final DepotDeclarations declarations;
    private final ProprietesService service;

    Dossiers(DepotDossiers depot, DepotDeclarations declarations, ProprietesService service) {
        this.depot = depot;
        this.declarations = declarations;
        this.service = service;
    }

    public VueDossier vue(UUID id) {
        Dossier dossier = depot.lire(id).orElseThrow(ErreurMetier::introuvable);
        return VueDossier.de(
                dossier, depot.verifications(id), declarations.lister(id), service.pieces());
    }

    /** La pièce reçue d'un registre pour un jeu du dossier. */
    public ResponseEntity<byte[]> piece(UUID dossier, String jeu, boolean apercu) {
        return envoyer(depot.piece(dossier, jeu).orElseThrow(ErreurMetier::introuvable), apercu);
    }

    /** La pièce jointe par la personne pour une de ses déclarations. */
    public ResponseEntity<byte[]> pieceDeclaree(UUID dossier, String code, boolean apercu) {
        return envoyer(
                declarations.piece(dossier, code).orElseThrow(ErreurMetier::introuvable), apercu);
    }

    /**
     * Une pièce est proposée en téléchargement. L'aperçu (affichage dans le navigateur) n'est
     * accordé qu'à un PDF : tout autre type reste un téléchargement d'octets bruts, et le
     * navigateur ne devine jamais le type ({@code nosniff}). La plateforme ne l'ouvre donc jamais
     * comme une de ses pages.
     */
    private static ResponseEntity<byte[]> envoyer(DepotDossiers.Contenu piece, boolean apercu) {
        boolean pdf = MediaType.APPLICATION_PDF_VALUE.equals(piece.type());
        ContentDisposition.Builder disposition =
                pdf && apercu ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition
                                .filename(piece.nom(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(piece.octets());
    }
}
