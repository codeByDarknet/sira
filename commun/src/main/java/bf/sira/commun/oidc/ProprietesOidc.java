package bf.sira.commun.oidc;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connexion OIDC d'un serveur d'application à session, sous le préfixe {@code sira.oidc}. Le
 * navigateur et le serveur ne joignent pas le fournisseur d'identité par la même adresse : les
 * points d'accès se règlent séparément. Par défaut ils sont déduits des deux adresses du royaume,
 * selon les chemins de Keycloak.
 *
 * @param emetteur adresse publique du royaume, valeur exacte de {@code iss} (ex. {@code
 *     http://identite.sira.localhost:8600/realms/sira-citoyens})
 * @param urlInterne adresse du même royaume sur le réseau {@code sira} (ex. {@code
 *     https://keycloak:8443/realms/sira-citoyens})
 * @param clientId identifiant du client, authentifié par son certificat (aucun secret)
 * @param urlPublique adresse publique de l'application (ex. {@code
 *     http://citoyen.sira.localhost:8600})
 * @param scopes scopes demandés
 * @param autorisationUri point d'autorisation, vu du navigateur
 * @param finSessionUri point de fin de session, vu du navigateur
 * @param jetonsUri point de jetons, joint en mTLS
 * @param jwksUri clés du royaume, lues en mTLS
 */
@ConfigurationProperties("sira.oidc")
public record ProprietesOidc(
        String emetteur,
        String urlInterne,
        String clientId,
        String urlPublique,
        @DefaultValue("openid") List<String> scopes,
        String autorisationUri,
        String finSessionUri,
        String jetonsUri,
        String jwksUri) {

    private static final String PROTOCOLE = "/protocol/openid-connect";

    public ProprietesOidc {
        exiger(emetteur, "sira.oidc.emetteur");
        exiger(clientId, "sira.oidc.client-id");
        exiger(urlPublique, "sira.oidc.url-publique");
        if (jetonsUri == null || jwksUri == null) {
            exiger(urlInterne, "sira.oidc.url-interne");
        }
        urlPublique = urlPublique.replaceAll("/+$", "");
        autorisationUri =
                autorisationUri != null ? autorisationUri : emetteur + PROTOCOLE + "/auth";
        finSessionUri = finSessionUri != null ? finSessionUri : emetteur + PROTOCOLE + "/logout";
        jetonsUri = jetonsUri != null ? jetonsUri : urlInterne + PROTOCOLE + "/token";
        jwksUri = jwksUri != null ? jwksUri : urlInterne + PROTOCOLE + "/certs";
    }

    private static void exiger(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("propriété obligatoire : " + nom);
        }
    }
}
