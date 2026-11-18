package bf.sira.demo.service.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Le formulaire de la démarche, sous le préfixe {@code sira.formulaire}. Les informations des
 * registres en découlent (ce sont les jeux de l'habilitation) ; seules les informations que la
 * personne déclare elle-même sont propres au service.
 *
 * @param declarations ce que la personne renseigne elle-même, dans l'ordre d'affichage
 * @param champs champs attendus pour chaque jeu de niveau {@code CHAMPS}, par code de jeu : Sira
 *     décrit les jeux sans leurs champs, l'écran les annonce avant que le registre ne réponde
 */
@Validated
@ConfigurationProperties("sira.formulaire")
public record ProprietesFormulaire(
        @DefaultValue List<@Valid Declaration> declarations,
        @DefaultValue Map<String, List<String>> champs) {

    /** Saisie libre, ou pièce jointe au format PDF. */
    public enum Nature {
        TEXTE,
        PIECE
    }

    /**
     * Une information déclarée par la personne. Son code ne contient pas de point : il ne peut
     * jamais désigner un jeu de registre.
     */
    public record Declaration(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,40}") String code,
            @NotBlank @Size(max = 120) String libelle,
            @Size(max = 200) String aide,
            @NotNull Nature nature) {}

    public Declaration declaration(String code) {
        return declarations.stream().filter(d -> d.code().equals(code)).findFirst().orElse(null);
    }
}
