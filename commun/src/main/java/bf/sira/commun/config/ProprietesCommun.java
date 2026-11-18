package bf.sira.commun.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Réglages communs à tous les composants, sous le préfixe {@code sira}.
 *
 * @param pki emplacement des clés et certificats du membre
 * @param http bornes des échanges HTTP
 * @param passerelle adresse de la passerelle, pour ses clients
 */
@ConfigurationProperties("sira")
public record ProprietesCommun(
        @DefaultValue Pki pki, @DefaultValue Http http, @DefaultValue Passerelle passerelle) {

    /**
     * @param repertoire contient {@code tls.crt/.key}, {@code cachet.crt/.key}, {@code
     *     chiffrement.crt/.key} et {@code ca.crt}
     */
    public record Pki(@DefaultValue("/run/sira/pki") Path repertoire) {}

    /**
     * @param tailleCorpsMax taille maximale d'un corps de requête reçu
     * @param delaiConnexion délai d'établissement d'une connexion sortante
     * @param delaiLecture délai d'attente d'une réponse
     */
    public record Http(
            @DefaultValue("256KB") DataSize tailleCorpsMax,
            @DefaultValue("2s") Duration delaiConnexion,
            @DefaultValue("5s") Duration delaiLecture) {}

    public record Passerelle(@DefaultValue("https://passerelle:8443") String url) {}
}
