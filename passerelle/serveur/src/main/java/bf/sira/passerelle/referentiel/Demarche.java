package bf.sira.passerelle.referentiel;

import bf.sira.passerelle.config.Formes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Une démarche administrative : ce pour quoi un service demande des informations.
 *
 * @param finalite dite au citoyen sur l'écran d'accord, et transmise aux registres
 * @param dureeAutorisationJours durée de l'accord du citoyen ; {@code 0} = usage unique
 */
public record Demarche(
        @NotNull @Pattern(regexp = Formes.CODE) String code,
        @NotBlank @Size(max = 200) String libelle,
        @NotBlank @Size(max = 500) String finalite,
        @Size(max = 500) String baseLegale,
        @NotNull @Pattern(regexp = Formes.CODE) String service,
        @NotNull @Min(0) @Max(3650) Integer dureeAutorisationJours,
        Boolean actif) {

    public Demarche {
        actif = actif == null || actif;
    }
}
