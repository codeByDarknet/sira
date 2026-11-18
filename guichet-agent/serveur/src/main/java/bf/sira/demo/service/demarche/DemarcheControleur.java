package bf.sira.demo.service.demarche;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.demo.service.configuration.ProprietesFormulaire;
import bf.sira.demo.service.configuration.ProprietesService;
import bf.sira.kit.ClientSira;
import bf.sira.kit.Demarche;
import bf.sira.kit.ErreurPasserelle;
import bf.sira.kit.Jeu;
import bf.sira.kit.Niveau;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * Présente la démarche : ce que le service vérifiera, les pièces que cela évite, et le formulaire
 * de la demande. Les informations des registres découlent des jeux de l'habilitation ; seules les
 * déclarations de la personne viennent de la configuration du service.
 */
@RestController
class DemarcheControleur {

    private static final Duration FRAICHEUR = Duration.ofMinutes(5);

    record Service(String code, String nom, String organisme) {}

    /**
     * @param champs champs attendus d'un jeu de niveau {@code CHAMPS} ; vide pour les autres
     */
    record JeuPresente(
            String code, String libelle, Niveau niveau, String registre, List<String> champs) {}

    record Presentation(
            Service service,
            String code,
            String libelle,
            List<JeuPresente> jeux,
            List<ProprietesFormulaire.Declaration> declarations,
            List<String> pieces) {}

    private record Connue(Demarche demarche, Instant lueLe) {}

    private final ClientSira sira;
    private final ProprietesService service;
    private final ProprietesFormulaire formulaire;
    private final Clock horloge;
    private volatile Connue connue;

    DemarcheControleur(
            ClientSira sira,
            ProprietesService service,
            ProprietesFormulaire formulaire,
            Clock horloge) {
        this.sira = sira;
        this.service = service;
        this.formulaire = formulaire;
        this.horloge = horloge;
    }

    @GetMapping("/api/demarche")
    Presentation presenter() {
        Demarche demarche = demarche();
        return new Presentation(
                new Service(service.code(), service.nom(), service.organisme()),
                demarche.code(),
                demarche.libelle(),
                demarche.jeux().stream().map(this::presenter).toList(),
                formulaire.declarations(),
                demarche.jeux().stream()
                        .map(jeu -> service.pieces().get(jeu.registre()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
    }

    private JeuPresente presenter(Jeu jeu) {
        List<String> champs =
                jeu.niveau() == Niveau.CHAMPS
                        ? formulaire.champs().getOrDefault(jeu.code(), List.of())
                        : List.of();
        return new JeuPresente(jeu.code(), jeu.libelle(), jeu.niveau(), jeu.registre(), champs);
    }

    /**
     * La description vient de Sira (ce sont les jeux de l'habilitation). Elle change rarement : une
     * copie de quelques minutes évite un appel par visiteur, et sert encore si Sira ne répond pas.
     */
    private Demarche demarche() {
        Connue copie = connue;
        if (copie != null && copie.lueLe().plus(FRAICHEUR).isAfter(horloge.instant())) {
            return copie.demarche();
        }
        try {
            Demarche lue = sira.decrireDemarche(service.demarche());
            connue = new Connue(lue, horloge.instant());
            return lue;
        } catch (ErreurPasserelle | ResourceAccessException echec) {
            if (copie != null) {
                return copie.demarche();
            }
            throw new ErreurMetier(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "indisponible",
                    "Service momentanément indisponible",
                    "La démarche n'est pas disponible pour le moment. Réessayez dans un instant.");
        }
    }
}
