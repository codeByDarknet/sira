package bf.sira.demo.service.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ce qui distingue un service d'un autre, sous le préfixe {@code sira.service} : le même binaire
 * sert les concours et les bourses.
 *
 * @param code code du service chez Sira ; doit égaler l'attribut « administration » des agents
 * @param nom nom affiché de la plateforme
 * @param organisme administration qui tient la plateforme
 * @param demarche code de la démarche habilitée, au nom de laquelle les demandes sont ouvertes
 * @param prefixeDossier préfixe des numéros de dossier
 * @param relance intervalle entre deux relances des demandes dont un registre n'a pas répondu
 * @param demandesParMinute demandes qu'un même navigateur peut ouvrir par minute
 * @param demandesParMinuteTotal demandes que la plateforme entière peut ouvrir par minute
 * @param pieces pièce que le citoyen aurait dû fournir, par code de registre
 */
@Validated
@ConfigurationProperties("sira.service")
public record ProprietesService(
        @NotBlank @Pattern(regexp = "[a-z][a-z-]{1,30}") String code,
        @NotBlank String nom,
        @NotBlank String organisme,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,60}") String demarche,
        @NotBlank @Pattern(regexp = "[A-Z]{2,4}") String prefixeDossier,
        @NotNull @DefaultValue("10s") Duration relance,
        @Min(1) @DefaultValue("5") int demandesParMinute,
        @Min(1) @DefaultValue("120") int demandesParMinuteTotal,
        @DefaultValue Map<String, String> pieces) {}
