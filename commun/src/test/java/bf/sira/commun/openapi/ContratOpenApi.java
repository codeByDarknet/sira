package bf.sira.commun.openapi;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Lecture d'une spécification OpenAPI de couche (fichier {@code <couche>/openapi.yaml}) et
 * contrôles de conformité utilisés par les tests de chaque module.
 *
 * <ul>
 *   <li>{@link #routes()} : les couples « MÉTHODE /chemin » déclarés dans le fichier ;
 *   <li>{@link #routesDesControleurs(String...)} : les mêmes couples, lus sur les contrôleurs
 *       Spring (annotations {@code @GetMapping}, {@code @PostMapping}…) ;
 *   <li>{@link #verifier} : une réponse réelle (statut, type de contenu, corps) est-elle celle que
 *       la spécification annonce ? Les schémas JSON sont contrôlés par la bibliothèque libre
 *       networknt json-schema-validator (dialecte OpenAPI 3.1, formats vérifiés).
 * </ul>
 */
public final class ContratOpenApi {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Map<String, ContratOpenApi> DEJA_LUS = new ConcurrentHashMap<>();
    private static final Set<String> METHODES =
            Set.of("get", "put", "post", "delete", "patch", "head", "options");

    private final String fichier;
    private final JsonNode document;
    private final SchemaRegistry registre;
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();
    private final AtomicInteger reponsesVerifiees = new AtomicInteger();

    private ContratOpenApi(String fichier, JsonNode document) {
        this.fichier = fichier;
        this.document = document;
        // Chaque fichier du dépôt a une adresse fictive qui garde son chemin : un « $ref » relatif
        // vers la spécification d'une autre couche (« ../journal/openapi.yaml#/… ») se résout.
        this.registre =
                SchemaRegistry.withDefaultDialect(
                        Dialects.getOpenApi31(),
                        b ->
                                b.schemas(ContratOpenApi::documentEnJson)
                                        .schemaRegistryConfig(
                                                SchemaRegistryConfig.builder()
                                                        .formatAssertionsEnabled(true)
                                                        .build()));
    }

    /** Lit {@code chemin} (relatif à la racine du dépôt), une seule fois par JVM. */
    public static ContratOpenApi lire(String chemin) {
        return DEJA_LUS.computeIfAbsent(chemin, ContratOpenApi::charger);
    }

    private static ContratOpenApi charger(String chemin) {
        Path fichier = racineDuDepot().resolve(chemin).normalize();
        try {
            return new ContratOpenApi(
                    chemin, YAMLMapper.builder().build().readTree(Files.readString(fichier)));
        } catch (IOException e) {
            throw new IllegalStateException("Spécification illisible : " + fichier, e);
        }
    }

    /** Remonte depuis le dossier courant jusqu'au dossier qui contient {@code mvnw}. */
    static Path racineDuDepot() {
        Path dossier = Path.of("").toAbsolutePath();
        while (dossier != null && !Files.exists(dossier.resolve("mvnw"))) {
            dossier = dossier.getParent();
        }
        if (dossier == null) {
            throw new IllegalStateException("Racine du dépôt introuvable (mvnw absent)");
        }
        return dossier;
    }

    private static final String BASE = "https://specifications.sira.invalid/";

    private static String adresse(String fichier) {
        return BASE + fichier;
    }

    /** Le contenu JSON du fichier du dépôt que désigne {@code iri}, ou {@code null}. */
    private static String documentEnJson(String iri) {
        if (!iri.startsWith(BASE)) {
            return null;
        }
        String chemin = iri.substring(BASE.length());
        int fragment = chemin.indexOf('#');
        return lire(fragment < 0 ? chemin : chemin.substring(0, fragment)).document.toString();
    }

    public JsonNode document() {
        return document;
    }

    /** Nombre de réponses réelles contrôlées avec succès depuis le démarrage de la JVM. */
    public int reponsesVerifiees() {
        return reponsesVerifiees.get();
    }

    // ------------------------------------------------------------------ routes

    /** Les opérations déclarées : « GET /v1/… », triées. */
    public Set<String> routes() {
        Set<String> routes = new TreeSet<>();
        document.path("paths")
                .properties()
                .forEach(chemin ->
                        chemin.getValue()
                                .properties()
                                .forEach(op -> {
                                    if (METHODES.contains(op.getKey())) {
                                        routes.add(
                                                op.getKey().toUpperCase() + " " + chemin.getKey());
                                    }
                                }));
        return routes;
    }

    /** Les opérations dont l'extension {@code x-sira-source} vaut {@code source}. */
    public Set<String> routesDeSource(String source) {
        Set<String> routes = new TreeSet<>();
        for (String route : routes()) {
            if (source.equals(operation(route).path("x-sira-source").asString(""))) {
                routes.add(route);
            }
        }
        return routes;
    }

    /** L'objet « Operation » de la route « GET /v1/… ». */
    public JsonNode operation(String route) {
        String[] parties = route.split(" ", 2);
        return document.path("paths").path(parties[1]).path(parties[0].toLowerCase());
    }

    /**
     * Les routes servies par les classes {@code @RestController} des paquets donnés, lues sur leurs
     * annotations (y compris les méthodes héritées d'une classe mère abstraite).
     */
    public static Set<String> routesDesControleurs(String... paquets) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new TreeSet<>();
        for (String paquet : paquets) {
            for (BeanDefinition definition : scanner.findCandidateComponents(paquet)) {
                Class<?> classe =
                        ClassUtils.resolveClassName(
                                definition.getBeanClassName(),
                                ContratOpenApi.class.getClassLoader());
                routes.addAll(routesDe(classe));
            }
        }
        return routes;
    }

    private static Set<String> routesDe(Class<?> classe) {
        RequestMapping base =
                AnnotatedElementUtils.findMergedAnnotation(classe, RequestMapping.class);
        List<String> prefixes = base == null ? List.of("") : chemins(base);
        Set<String> routes = new TreeSet<>();
        // Méthodes de la classe et de ses classes mères, publiques ou non, sans doublon.
        for (Method methode : ReflectionUtils.getUniqueDeclaredMethods(classe)) {
            if (methode.isBridge() || methode.isSynthetic()) {
                continue;
            }
            RequestMapping m =
                    AnnotatedElementUtils.findMergedAnnotation(methode, RequestMapping.class);
            if (m == null) {
                continue;
            }
            List<String> verbes =
                    m.method().length == 0
                            ? List.of("TOUTES")
                            : java.util.Arrays.stream(m.method()).map(Enum::name).toList();
            for (String prefixe : prefixes) {
                for (String suite : chemins(m)) {
                    for (String verbe : verbes) {
                        routes.add(verbe + " " + normaliser(prefixe + suite));
                    }
                }
            }
        }
        return routes;
    }

    private static List<String> chemins(RequestMapping m) {
        return m.path().length == 0 ? List.of("") : List.of(m.path());
    }

    /** « {id:[0-9]+} » devient « {id} » ; pas de « / » final. */
    private static String normaliser(String chemin) {
        String c = chemin.replaceAll("\\{([A-Za-z_]+):[^}]*}", "{$1}");
        return c.length() > 1 && c.endsWith("/") ? c.substring(0, c.length() - 1) : c;
    }

    // ------------------------------------------------------------------ exemples

    /**
     * Valide chaque {@code example} de type de contenu JSON contre son schéma. Renvoie le nombre
     * d'exemples contrôlés ; lève une {@link AssertionError} au premier exemple non conforme.
     */
    public int validerExemples() {
        int nombre = 0;
        for (String route : routes()) {
            JsonNode operation = operation(route);
            JsonNode corps = resoudre(operation.path("requestBody"));
            nombre += validerContenus(route + " (corps de requête)", corps.path("content"));
            for (var reponse : resoudre(operation.path("responses")).properties()) {
                nombre +=
                        validerContenus(
                                route + " → " + reponse.getKey(),
                                resoudre(reponse.getValue()).path("content"));
            }
        }
        return nombre;
    }

    private int validerContenus(String ou, JsonNode contenus) {
        int nombre = 0;
        for (var contenu : contenus.properties()) {
            JsonNode exemple = contenu.getValue().get("example");
            if (exemple != null && estJson(contenu.getKey())) {
                List<Error> erreurs =
                        schemaPour(pointeur(contenu.getValue().path("schema"), ou, contenu))
                                .validate(exemple);
                if (!erreurs.isEmpty()) {
                    throw new AssertionError(
                            fichier + " : exemple non conforme, " + ou + " " + erreurs);
                }
                nombre++;
            }
        }
        return nombre;
    }

    // ------------------------------------------------------------------ réponses réelles

    /**
     * Contrôle une réponse réelle. Les chemins absents de la spécification sont ignorés (ils
     * relèvent d'une autre couche) et la méthode renvoie {@code false}.
     *
     * @param typeContenu en-tête {@code Content-Type} reçu (peut être {@code null})
     * @param corps corps reçu, vide si aucun
     */
    public boolean verifier(
            String methode, String chemin, int statut, String typeContenu, byte[] corps) {
        String sansRequete =
                chemin.contains("?") ? chemin.substring(0, chemin.indexOf('?')) : chemin;
        Optional<String> modele = modele(sansRequete);
        if (modele.isEmpty()) {
            return false;
        }
        String route = methode.toUpperCase() + " " + modele.get();
        JsonNode operation = operation(route);
        if (operation.isMissingNode()) {
            return false;
        }
        String ou = fichier + " : " + route + " → " + statut;
        JsonNode reponse = resoudre(operation.path("responses").path(String.valueOf(statut)));
        if (reponse.isMissingNode()) {
            throw new AssertionError(ou + " : statut non déclaré. Corps : " + apercu(corps));
        }
        JsonNode contenus = reponse.path("content");
        if (corps == null || corps.length == 0) {
            if (!contenus.isEmpty() && statut != 304) {
                throw new AssertionError(ou + " : corps vide alors qu'un contenu est déclaré");
            }
            reponsesVerifiees.incrementAndGet();
            return true;
        }
        if (contenus.isEmpty()) {
            throw new AssertionError(ou + " : corps reçu alors qu'aucun contenu n'est déclaré");
        }
        String type = typeContenu == null ? "" : typeContenu.split(";")[0].trim();
        JsonNode contenu = contenus.get(type);
        if (contenu == null) {
            throw new AssertionError(
                    ou + " : type « " + type + " » non déclaré (" + contenus.propertyNames() + ")");
        }
        if (estJson(type) && contenu.has("schema")) {
            JsonNode recu;
            try {
                recu = JSON.readTree(corps);
            } catch (RuntimeException e) {
                throw new AssertionError(ou + " : corps JSON illisible : " + apercu(corps), e);
            }
            List<Error> erreurs =
                    schemaPour(pointeur(contenu.path("schema"), ou, null)).validate(recu);
            if (!erreurs.isEmpty()) {
                throw new AssertionError(ou + " : réponse non conforme " + erreurs + "\n" + recu);
            }
        }
        reponsesVerifiees.incrementAndGet();
        return true;
    }

    /** Le modèle de chemin le plus précis (le moins de variables) qui reconnaît {@code chemin}. */
    private Optional<String> modele(String chemin) {
        List<String> candidats = new ArrayList<>();
        document.path("paths")
                .propertyNames()
                .forEach(m -> {
                    if (Pattern.matches(m.replaceAll("\\{[^}]+}", "[^/]+"), chemin)) {
                        candidats.add(m);
                    }
                });
        return candidats.stream()
                .min(Comparator.comparingLong(m -> m.chars().filter(c -> c == '{').count()));
    }

    // ------------------------------------------------------------------ outils

    /** Suit un {@code $ref} interne (« #/components/… »). */
    private JsonNode resoudre(JsonNode noeud) {
        JsonNode courant = noeud;
        for (int i = 0; i < 10 && courant.has("$ref"); i++) {
            courant = document.at(courant.get("$ref").asString().substring(1));
        }
        return courant;
    }

    /**
     * Le schéma à valider, désigné par un pointeur JSON dans le document : les {@code $ref} vers
     * {@code #/components/schemas/…} se résolvent alors normalement.
     */
    private String pointeur(JsonNode schema, String ou, Object contexte) {
        if (schema.has("$ref")) {
            return schema.get("$ref").asString();
        }
        // Schéma en ligne : on retrouve son emplacement en le cherchant dans le document.
        String trouve = chercher(document, schema, "");
        if (trouve == null) {
            throw new AssertionError(fichier + " : schéma introuvable pour " + ou + " " + contexte);
        }
        return "#" + trouve;
    }

    private static String chercher(JsonNode ici, JsonNode cible, String chemin) {
        if (ici == cible) {
            return chemin;
        }
        if (ici.isContainer()) {
            for (var enfant : ici.properties()) {
                String r =
                        chercher(
                                enfant.getValue(),
                                cible,
                                chemin
                                        + "/"
                                        + enfant.getKey().replace("~", "~0").replace("/", "~1"));
                if (r != null) {
                    return r;
                }
            }
            for (int i = 0; ici.isArray() && i < ici.size(); i++) {
                String r = chercher(ici.get(i), cible, chemin + "/" + i);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private Schema schemaPour(String pointeur) {
        return schemas.computeIfAbsent(
                pointeur,
                p -> registre.getSchema(JSON.createObjectNode().put("$ref", resoudreAdresse(p))));
    }

    /**
     * Adresse absolue d'un {@code $ref} écrit dans ce fichier. Le fragment est un pointeur JSON :
     * ses caractères hors URI (« { », « } »…) sont encodés.
     */
    private String resoudreAdresse(String ref) {
        int diese = ref.indexOf('#');
        String document = diese < 0 ? ref : ref.substring(0, diese);
        String fragment = diese < 0 ? "" : ref.substring(diese + 1);
        StringBuilder encode = new StringBuilder();
        for (byte b : fragment.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if (Character.isLetterOrDigit(c) && c < 128 || "/~-._".indexOf(c) >= 0) {
                encode.append(c);
            } else {
                encode.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        String base =
                document.isEmpty()
                        ? adresse(fichier)
                        : URI.create(adresse(fichier)).resolve(document).toString();
        return base + "#" + encode;
    }

    // ------------------------------------------------------------------ branchements

    /**
     * Contrôle une réponse réelle contre la première des spécifications qui décrit sa route. Une
     * route qu'aucune ne décrit est laissée passer : les tests qui visent exprès une route inconnue
     * restent possibles, et la comparaison des routes est faite à part.
     */
    public static boolean verifierParmi(
            List<ContratOpenApi> specifications,
            String methode,
            String chemin,
            int statut,
            String typeContenu,
            byte[] corps) {
        for (ContratOpenApi specification : specifications) {
            if (specification.verifier(methode, chemin, statut, typeContenu, corps)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intercepteur de {@code RestClient} : chaque réponse reçue par un test est contrôlée, puis
     * rendue intacte au test (le corps est lu une fois et gardé en mémoire).
     */
    public static ClientHttpRequestInterceptor intercepteur(ContratOpenApi... specifications) {
        List<ContratOpenApi> liste = List.of(specifications);
        return (requete, envoye, execution) -> {
            ClientHttpResponse reponse = execution.execute(requete, envoye);
            byte[] corps = reponse.getBody().readAllBytes();
            MediaType type = reponse.getHeaders().getContentType();
            verifierParmi(
                    liste,
                    requete.getMethod().name(),
                    requete.getURI().getRawPath(),
                    reponse.getStatusCode().value(),
                    type == null ? null : type.toString(),
                    corps);
            return new ReponseRelue(reponse, corps);
        };
    }

    /** Pour MockMvc : {@code .alwaysDo(ContratOpenApi.controle(...))}. */
    public static ResultHandler controle(ContratOpenApi... specifications) {
        List<ContratOpenApi> liste = List.of(specifications);
        return resultat ->
                verifierParmi(
                        liste,
                        resultat.getRequest().getMethod(),
                        resultat.getRequest().getRequestURI(),
                        resultat.getResponse().getStatus(),
                        resultat.getResponse().getContentType(),
                        resultat.getResponse().getContentAsByteArray());
    }

    /** Réponse dont le corps a déjà été lu : le test la relit depuis la mémoire. */
    private record ReponseRelue(ClientHttpResponse origine, byte[] corps)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return origine.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return origine.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return origine.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(corps);
        }

        @Override
        public void close() {
            origine.close();
        }
    }

    private static boolean estJson(String type) {
        return type.equals("application/json") || type.endsWith("+json");
    }

    private static String apercu(byte[] corps) {
        if (corps == null) {
            return "(aucun)";
        }
        String texte = new String(corps, java.nio.charset.StandardCharsets.UTF_8);
        return texte.length() > 400 ? texte.substring(0, 400) + "…" : texte;
    }
}
