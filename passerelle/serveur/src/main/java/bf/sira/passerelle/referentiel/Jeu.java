package bf.sira.passerelle.referentiel;

import bf.sira.passerelle.config.Formes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Un jeu de données : une question qu'un registre accepte, et la forme de la réponse (contrat
 * §7.3). Selon le niveau, il porte une règle, une liste de champs ou un type de document.
 *
 * @param libelle affiché au citoyen sur l'écran d'accord
 * @param validite durée ISO 8601 pendant laquelle une réponse reste réutilisable
 */
public record Jeu(
        @NotNull @Pattern(regexp = Formes.CODE_JEU) String code,
        @NotNull @Pattern(regexp = Formes.CODE) String registre,
        @NotNull Niveau niveau,
        @NotBlank @Size(max = 200) String libelle,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 32) String validite,
        @Valid Regle regle,
        @Size(min = 1, max = 20) List<@NotNull @Pattern(regexp = CHAMP) String> champs,
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String document,
        Boolean actif) {

    static final String CHAMP = "[A-Za-z0-9_.]{1,128}";

    public Jeu {
        actif = actif == null || actif;
    }

    /**
     * @param valeur seuil ou valeur attendue ; absente pour les opérateurs qui n'en demandent pas
     */
    public record Regle(
            @NotNull @Pattern(regexp = CHAMP) String champ,
            @NotNull Operateur operateur,
            JsonNode valeur) {}
}
