package bf.sira.commun.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Réglages de base de tout composant Sira, appliqués avec la priorité la plus faible : chacun reste
 * modifiable par configuration. Le mode {@code sira.mtls.mode} choisit le profil réseau :
 *
 * <ul>
 *   <li>{@code complet} (défaut) : API en HTTPS sur 8443, TLS 1.3, certificat client exigé ;
 *   <li>{@code client} : serveur HTTP sur 8080 derrière son proxy, appels sortants en mTLS ;
 *   <li>{@code aucun} : ni clés ni mTLS (tests, registre existant simulé).
 * </ul>
 *
 * Le défaut est le plus strict : un composant mal configuré ne démarre pas, il ne s'ouvre pas.
 */
public class ReglagesParDefaut implements EnvironmentPostProcessor, Ordered {

    public static final String BUNDLE = "sira";

    private static final String SOURCE = "sira-reglages-par-defaut";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environnement, SpringApplication application) {
        String mode = environnement.getProperty("sira.mtls.mode", "complet");
        Map<String, Object> reglages = new LinkedHashMap<>();
        // Contrat §7 : tous les corps JSON sont en snake_case.
        reglages.put("spring.jackson.property-naming-strategy", "SNAKE_CASE");
        reglages.put("server.error.whitelabel.enabled", "false");
        reglages.put("management.endpoints.web.exposure.include", "health");
        reglages.put("management.endpoint.health.probes.enabled", "true");
        switch (mode) {
            case "complet" -> {
                materiel(reglages);
                gestion(reglages);
                reglages.put("server.port", "8443");
                reglages.put("server.ssl.bundle", BUNDLE);
                reglages.put("server.ssl.client-auth", "need");
                reglages.put("server.ssl.enabled-protocols", "TLSv1.3");
            }
            case "client" -> {
                materiel(reglages);
                gestion(reglages);
                reglages.put("server.port", "8080");
                reglages.put("server.servlet.session.cookie.http-only", "true");
                reglages.put("server.servlet.session.cookie.same-site", "lax");
                reglages.put("server.servlet.session.tracking-modes", "cookie");
                reglages.put("server.servlet.session.timeout", "30m");
            }
            case "aucun" -> {}
            default ->
                    throw new IllegalStateException(
                            "sira.mtls.mode doit valoir complet, client ou aucun");
        }
        environnement.getPropertySources().addLast(new MapPropertySource(SOURCE, reglages));
    }

    /** Clé et certificat de transport, et l'autorité Sira pour seule ancre de confiance. */
    private static void materiel(Map<String, Object> reglages) {
        String repertoire = "file:${sira.pki.repertoire:/run/sira/pki}/";
        String bundle = "spring.ssl.bundle.pem." + BUNDLE;
        reglages.put(bundle + ".keystore.certificate", repertoire + "tls.crt");
        reglages.put(bundle + ".keystore.private-key", repertoire + "tls.key");
        reglages.put(bundle + ".truststore.certificate", repertoire + "ca.crt");
        reglages.put(bundle + ".protocol", "TLSv1.3");
        reglages.put(bundle + ".options.enabled-protocols", "TLSv1.3");
    }

    /**
     * Port de gestion en clair, en boucle locale : il ne sert qu'à la sonde de santé du conteneur.
     */
    private static void gestion(Map<String, Object> reglages) {
        reglages.put("management.server.port", "8081");
        reglages.put("management.server.address", "127.0.0.1");
        reglages.put("management.server.ssl.enabled", "false");
    }

    /** Après la lecture des fichiers de configuration, pour connaître le mode choisi. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
