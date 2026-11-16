package bf.sira.journal;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/** Le citoyen lit qui a consulté quoi, et conteste un accès (contrat §7.2). */
@RestController
@RequestMapping("/v1/citoyen/journal")
class JournalCitoyenControleur {

    private final CitoyensJournal citoyens;
    private final LectureJournal lecture;
    private final Signalements signalements;

    JournalCitoyenControleur(
            CitoyensJournal citoyens, LectureJournal lecture, Signalements signalements) {
        this.citoyens = citoyens;
        this.lecture = lecture;
        this.signalements = signalements;
    }

    record NouveauSignalement(@NotBlank @Size(min = 10, max = 500) String motif) {}

    @GetMapping
    Page<Ligne> lister(
            @AuthenticationPrincipal Jwt jeton,
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(required = false) @Size(max = 64) String service,
            @Valid Pagination pagination) {
        return lecture.duCitoyen(citoyens.courant(jeton), type, service, pagination);
    }

    @PostMapping("/{id}/signalement")
    @ResponseStatus(HttpStatus.CREATED)
    Signalements.Recu signaler(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody NouveauSignalement signalement) {
        return signalements.signaler(citoyens.courant(jeton), id, signalement.motif());
    }
}
