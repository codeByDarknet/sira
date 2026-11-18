package bf.sira.passerelle.referentiel;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.passerelle.config.Formes;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * Une administration raccordée au socle.
 *
 * @param retoursAutorises adresses exactes vers lesquelles le navigateur d'un citoyen peut revenir
 *     après son accord (canal REDIRECTION)
 */
public record MembreDeclare(
        @NotNull @Pattern(regexp = Formes.CODE) String code,
        @NotBlank @Size(max = 200) String nom,
        @NotBlank @Size(max = 200) String organisme,
        @NotEmpty Set<@NotNull RoleMembre> roles,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> retoursAutorises,
        Boolean actif) {

    public MembreDeclare {
        retoursAutorises = retoursAutorises == null ? List.of() : List.copyOf(retoursAutorises);
        actif = actif == null || actif;
    }

    /** L'identité que porte son certificat : elle découle du code, elle ne se saisit pas. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String identite() {
        if (code == null || roles == null) {
            return null;
        }
        return (roles.contains(RoleMembre.SERVICE)
                        ? IdentiteMembre.service(code)
                        : IdentiteMembre.registre(code))
                .uri();
    }
}
