package bf.sira.connecteur;

import bf.sira.commun.identite.Autorites;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Tout ce qui distingue un connecteur d'un autre, sous le préfixe {@code sira.connecteur}. Le même
 * binaire sert tous les registres.
 *
 * @param registre le registre protégé
 * @param politique fichier de politique locale, monté par l'administration
 * @param fuseau fuseau dans lequel le registre compte les jours (âges, échéances)
 * @param appelant seul membre admis à présenter son certificat sur l'API : la passerelle. La
 *     requête doit aussi porter son cachet.
 */
@Validated
@ConfigurationProperties("sira.connecteur")
public record ProprietesConnecteur(
        @NotNull @Valid Registre registre,
        @DefaultValue("/etc/sira/politique.yaml") Path politique,
        @DefaultValue("Africa/Ouagadougou") ZoneId fuseau,
        @DefaultValue("PASSERELLE") Appelant appelant) {

    /** Le membre qui remet les requêtes au connecteur. */
    public enum Appelant {
        PASSERELLE(Autorites.PASSERELLE);

        private final String autorite;

        Appelant(String autorite) {
            this.autorite = autorite;
        }

        public String autorite() {
            return autorite;
        }
    }

    /**
     * @param code code du registre : son identité est {@code urn:sira:registre:<code>}
     * @param url adresse du registre de démonstration, lu par son API interne ; exclut {@code
     *     raccordement}
     * @param raccordement fichier qui décrit comment lire le registre (adresse, chemin,
     *     authentification, correspondance des champs) ; exclut {@code url}
     * @param httpAdmis admet un registre joint en http simple : démonstration et tests seulement
     * @param hotesPermis hôtes qu'un fichier de raccordement peut nommer même s'ils sont sur un
     *     réseau privé ; tout autre hôte doit n'avoir que des adresses publiques
     * @param delaiConnexion avec {@code delaiLecture}, reste sous les 3 secondes que la passerelle
     *     accorde au connecteur (contrat §6)
     * @param tailleFicheMax taille maximale acceptée pour une fiche
     * @param tailleDocumentMax taille maximale acceptée pour un document
     */
    public record Registre(
            @NotNull @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?") String code,
            @Pattern(regexp = "https?://[A-Za-z0-9.:\\[\\]-]+(?:/[A-Za-z0-9._~/-]*)?") String url,
            Path raccordement,
            @DefaultValue("false") boolean httpAdmis,
            @DefaultValue List<String> hotesPermis,
            @DefaultValue("500ms") @DurationMax(seconds = 10) Duration delaiConnexion,
            @DefaultValue("2s") @DurationMax(seconds = 10) Duration delaiLecture,
            @DefaultValue("256KB") DataSize tailleFicheMax,
            @DefaultValue("2MB") DataSize tailleDocumentMax) {}
}
