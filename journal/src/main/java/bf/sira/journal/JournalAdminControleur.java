package bf.sira.journal;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.jetons.Acteur;
import bf.sira.journal.Signalements.Signalement;
import bf.sira.journal.VerificationJournal.Verification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** L'administration lit le journal, en vérifie l'intégrité et répond aux signalements. */
@RestController
@RequestMapping("/v1/admin")
class JournalAdminControleur {

    private final LectureJournal lecture;
    private final VerificationJournal verification;
    private final Signalements signalements;

    JournalAdminControleur(
            LectureJournal lecture, VerificationJournal verification, Signalements signalements) {
        this.lecture = lecture;
        this.verification = verification;
        this.signalements = signalements;
    }

    record Traitement(@NotBlank @Size(min = 10, max = 1000) String reponse) {}

    @GetMapping("/journal")
    Page<Ligne> lister(
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(required = false) @Size(max = 64) String service,
            @RequestParam(required = false) @Size(max = 64) String registre,
            @Valid Pagination pagination) {
        return lecture.tout(type, service, registre, pagination);
    }

    @GetMapping("/journal/verification")
    Verification verifier() {
        return verification.verifier();
    }

    @GetMapping("/signalements")
    Page<Signalement> signalements(
            @RequestParam(required = false) @Pattern(regexp = "|A_TRAITER|TRAITE") String etat,
            @Valid Pagination pagination) {
        return signalements.lister(etat, pagination);
    }

    @PostMapping("/signalements/{id}/traitement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void traiter(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody Traitement traitement) {
        signalements.traiter(id, traitement.reponse(), Acteur.de(jeton));
    }
}
