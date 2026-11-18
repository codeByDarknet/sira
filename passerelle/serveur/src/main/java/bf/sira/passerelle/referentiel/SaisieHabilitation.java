package bf.sira.passerelle.referentiel;

import bf.sira.passerelle.config.Formes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ce que l'instructeur saisit : qui demande, pour quelle démarche, quels jeux, et pourquoi. */
public record SaisieHabilitation(
        @NotNull @Pattern(regexp = Formes.CODE) String service,
        @NotNull @Pattern(regexp = Formes.CODE) String demarche,
        @NotNull @Size(min = 1, max = 50)
                List<@NotNull @Pattern(regexp = Formes.CODE_JEU) String> jeux,
        @NotBlank @Size(max = 2000) String justification) {}
