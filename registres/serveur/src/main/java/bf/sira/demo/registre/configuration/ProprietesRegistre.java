package bf.sira.demo.registre.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ce qui distingue un registre d'un autre, sous le préfixe {@code sira.registre} : le même binaire
 * sert l'état civil, la nationalité, le casier et les diplômes.
 *
 * @param code code du registre ; doit égaler l'attribut « administration » des agents admis
 * @param nom nom affiché du registre
 * @param organisme administration qui tient le registre, portée sur les documents
 * @param dossier dossier qui contient {@code registre.yaml} et {@code personnes.json}
 * @param dureeSilence temps pendant lequel un appel est retenu en mode de panne « MUET »
 * @param champs champs d'une fiche, dans l'ordre d'affichage
 * @param documents documents délivrés, par type
 */
@Validated
@ConfigurationProperties("sira.registre")
public record ProprietesRegistre(
        @NotBlank @Pattern(regexp = "[a-z][a-z-]{1,30}") String code,
        @NotBlank String nom,
        @NotBlank String organisme,
        @NotNull Path dossier,
        @DefaultValue("30s") Duration dureeSilence,
        @NotEmpty @Valid List<Champ> champs,
        @NotEmpty @Valid Map<String, Document> documents) {

    public enum Type {
        TEXTE,
        DATE,
        NOMBRE,
        BOOLEEN,
        CHOIX
    }

    /**
     * @param modifiable l'agent peut corriger ce champ depuis le back-office
     * @param facultatif le champ peut être vide
     * @param minimum borne basse d'un {@code NOMBRE}
     * @param maximum borne haute d'un {@code NOMBRE}
     * @param choix valeurs admises d'un {@code CHOIX}, avec leur libellé
     */
    public record Champ(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,40}") String nom,
            @NotBlank String libelle,
            @NotNull Type type,
            boolean modifiable,
            boolean facultatif,
            @DefaultValue("0") double minimum,
            @DefaultValue("1000000") double maximum,
            @DefaultValue Map<String, String> choix) {}

    /**
     * @param titre titre imprimé en tête du document
     * @param champs champs de la fiche repris dans le document, dans l'ordre
     */
    public record Document(@NotBlank String titre, @NotEmpty List<String> champs) {}

    public Optional<Champ> champ(String nom) {
        return champs.stream().filter(champ -> champ.nom().equals(nom)).findFirst();
    }
}
