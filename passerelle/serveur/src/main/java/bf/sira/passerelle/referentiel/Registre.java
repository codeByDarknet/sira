package bf.sira.passerelle.referentiel;

import bf.sira.passerelle.config.Formes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Un registre joignable par son connecteur.
 *
 * @param membre l'administration qui le tient
 * @param adresse adresse du connecteur ; seul HTTPS est admis
 * @param transport {@code DIRECT} : la passerelle joint le connecteur en mTLS
 */
public record Registre(
        @NotNull @Pattern(regexp = Formes.CODE) String code,
        @NotBlank @Size(max = 200) String nom,
        @NotNull @Pattern(regexp = Formes.CODE) String membre,
        @NotNull @Pattern(regexp = ADRESSE) String adresse,
        @NotNull @Pattern(regexp = "DIRECT") String transport,
        Boolean actif) {

    static final String ADRESSE =
            "https://[A-Za-z0-9]([A-Za-z0-9.-]{0,252}[A-Za-z0-9])?(:[0-9]{1,5})?";

    public Registre {
        actif = actif == null || actif;
    }
}
