package bf.sira.passerelle.collecte;

import bf.sira.commun.jetons.Acteur;
import bf.sira.consentement.Citoyens;
import bf.sira.passerelle.referentiel.Jeu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EssaiControleur {

    private final EssaiJeu essai;

    EssaiControleur(EssaiJeu essai) {
        this.essai = essai;
    }

    record Essai(
            @NotNull @Valid Jeu jeu,
            @NotNull @Pattern(regexp = Citoyens.FORME_IDENTIFIANT) String identifiantUnique) {}

    record ResultatEssai(Map<String, Object> resultat) {}

    @PostMapping("/v1/admin/jeux/essai")
    ResultatEssai essayer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody Essai essai) {
        return new ResultatEssai(
                this.essai.essayer(essai.jeu(), essai.identifiantUnique(), Acteur.de(jeton)));
    }
}
