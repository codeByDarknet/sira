package bf.sira.consentement;

import bf.sira.commun.donnees.Page;
import bf.sira.commun.donnees.Pagination;
import bf.sira.commun.donnees.Sql;
import bf.sira.consentement.DemandeCitoyen.Decision;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les routes du citoyen (contrat §7.2). Le citoyen est celui du jeton présenté par l'espace citoyen
 * : aucun paramètre ne le désigne.
 */
@RestController
@RequestMapping("/v1/citoyen")
class CitoyenControleur {

    private static final Duration PERIODE_RESUME = Duration.ofDays(30);

    private final Citoyens citoyens;
    private final Accords accords;
    private final Autorisations autorisations;
    private final JdbcClient sql;
    private final Clock horloge;

    CitoyenControleur(
            Citoyens citoyens,
            Accords accords,
            Autorisations autorisations,
            JdbcClient sql,
            Clock horloge) {
        this.citoyens = citoyens;
        this.accords = accords;
        this.autorisations = autorisations;
        this.sql = sql;
        this.horloge = horloge;
    }

    record Rattachement(@NotNull @Pattern(regexp = "[A-Za-z0-9 -]{1,16}") String codeCourt) {}

    enum Choix {
        ACCEPTER,
        REFUSER
    }

    /**
     * @param codeVerification le code que montre l'écran du service, tapé par le citoyen ; exigé
     *     pour accepter une demande qui en porte un
     */
    record DecisionPrise(
            @NotNull Choix decision, @Pattern(regexp = "[0-9 ]{1,16}") String codeVerification) {}

    record Resume(
            long demandesEnAttente,
            long autorisationsActives,
            // La stratégie snake_case ne sépare pas un nombre du mot qui le précède.
            @JsonProperty("consultations_30_jours") long consultations30Jours) {}

    @GetMapping("/demandes")
    Page<DemandeCitoyen> demandes(
            @AuthenticationPrincipal Jwt jeton,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{0,32}") String etat,
            @Valid Pagination pagination) {
        return accords.lister(citoyen(jeton), etat, pagination);
    }

    @PostMapping("/demandes/rattachement")
    DemandeCitoyen rattacherParCode(
            @AuthenticationPrincipal Jwt jeton, @Valid @RequestBody Rattachement rattachement) {
        return accords.rattacher(citoyen(jeton), rattachement.codeCourt());
    }

    @PostMapping("/demandes/{id}/rattachement")
    DemandeCitoyen rattacher(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        return accords.rattacher(citoyen(jeton), id);
    }

    @GetMapping("/demandes/{id}")
    DemandeCitoyen demande(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        return accords.lire(citoyen(jeton), id);
    }

    @PostMapping("/demandes/{id}/decision")
    Decision decider(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable UUID id,
            @Valid @RequestBody DecisionPrise decision) {
        return accords.decider(
                citoyen(jeton),
                id,
                decision.decision() == Choix.ACCEPTER,
                decision.codeVerification());
    }

    @GetMapping("/autorisations")
    Page<AutorisationCitoyen> autorisations(
            @AuthenticationPrincipal Jwt jeton,
            @RequestParam(required = false) @Pattern(regexp = "|ACTIVE|RETIREE|EXPIREE")
                    String etat,
            @Valid Pagination pagination) {
        return autorisations.lister(citoyen(jeton), etat, pagination);
    }

    @GetMapping("/autorisations/{id}")
    AutorisationCitoyen autorisation(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        return autorisations.lire(citoyen(jeton), id);
    }

    @DeleteMapping("/autorisations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        autorisations.retirer(citoyen(jeton), id);
    }

    @GetMapping("/resume")
    Resume resume(@AuthenticationPrincipal Jwt jeton) {
        String citoyen = citoyen(jeton);
        var maintenant = Sql.date(horloge.instant());
        return sql.sql(
                        "SELECT (SELECT count(*) FROM demande WHERE citoyen = :citoyen AND etat ="
                                + " 'EN_ATTENTE_ACCORD' AND expire_le > :maintenant), (SELECT"
                                + " count(*) FROM autorisation WHERE citoyen = :citoyen AND"
                                + " retiree_le IS NULL AND expire_le > :maintenant), (SELECT"
                                + " count(*) FROM journal WHERE citoyen = :journal AND type IN"
                                + " ('CONSULTATION', 'REUTILISATION') AND resultat = 'REUSSI' AND"
                                + " horodatage > :debut)")
                .param("citoyen", citoyen)
                .param("maintenant", maintenant)
                .param("journal", citoyens.pseudonymeJournal(citoyen))
                .param("debut", Sql.date(horloge.instant().minus(PERIODE_RESUME)))
                .query((ligne, i) ->
                        new Resume(ligne.getLong(1), ligne.getLong(2), ligne.getLong(3)))
                .single();
    }

    /**
     * Le citoyen du jeton, à qui sont d'abord remises les demandes par notification qui le
     * désignent.
     */
    private String citoyen(Jwt jeton) {
        String citoyen = citoyens.courant(jeton);
        accords.recevoir(citoyen);
        return citoyen;
    }
}
