package bf.sira.connecteur.requetes;

import bf.sira.connecteur.Niveau;
import bf.sira.connecteur.regles.Operateur;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Le contenu métier d'une requête signée par la passerelle (contrat §8). */
public record Requete(
        @NotNull @Valid Sujet sujet,
        @NotNull @Valid Demandeur demandeur,
        @NotNull @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String demarche,
        @NotBlank @Size(max = 500) String finalite,
        @NotNull @Valid Autorisation autorisation,
        @NotNull @Valid Jeu jeu) {

    /**
     * @param identifiantUnique il rejoint l'adresse appelée chez le registre : lettres, chiffres et
     *     tirets seulement
     */
    public record Sujet(
            @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String identifiantUnique,
            @NotNull @Pattern(regexp = "[A-Za-z0-9_=+/.-]{1,128}") String pseudonyme) {}

    /**
     * @param membre un service ; ou la passerelle elle-même, quand un instructeur essaie un jeu sur
     *     une personne fictive avant de le publier (l'attestation n'est alors lisible que par elle)
     */
    public record Demandeur(
            @NotNull @Pattern(regexp = "urn:sira:service:[a-z0-9-]{1,64}|urn:sira:socle:passerelle")
                    String membre,
            @NotBlank @Size(max = 32768) String certificatChiffrement) {}

    public record Autorisation(
            @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String id,
            Instant accordeeLe,
            @Pattern(regexp = "[A-Z_]{1,32}") String canal) {}

    /**
     * @param validite durée de validité souhaitée pour l'attestation ; la politique locale la
     *     plafonne
     */
    public record Jeu(
            @NotNull @Pattern(regexp = "[a-z0-9][a-z0-9.-]{0,127}") String code,
            @NotNull Niveau niveau,
            @Valid RegleRecue regle,
            @Size(min = 1, max = 20) List<@NotNull @Size(max = 128) String> champs,
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String document,
            Duration validite) {

        /** Ce qui est demandé au registre, pour le journal : des noms, jamais une valeur. */
        public String objet() {
            return switch (niveau) {
                case STATUT -> regle == null ? null : regle.champ() + " " + regle.operateur();
                case CHAMPS -> champs == null ? null : String.join(", ", champs);
                case DOCUMENT -> document;
            };
        }
    }

    public record RegleRecue(
            @NotNull @Size(max = 128) String champ,
            @NotNull Operateur operateur,
            JsonNode valeur) {}
}
