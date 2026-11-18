package bf.sira.connecteur.registre;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduit la réponse d'un registre en une fiche aux noms qu'emploient les questions. Seuls les
 * champs nommés dans la correspondance sont recopiés : le reste de la réponse est oublié aussitôt.
 *
 * <p>Deux écritures d'un même emplacement :
 *
 * <ul>
 *   <li>pointeur JSON (RFC 6901) : {@code /naissance/date}, {@code /nationalites/0/code} ;
 *   <li>chemin simple : {@code naissance.date}, {@code nationalites[0].code}.
 * </ul>
 *
 * Le segment {@code *} (ou {@code [*]}) parcourt tous les éléments d'une liste : le résultat est
 * alors une liste, vide si rien n'y figure. Un emplacement absent laisse le champ absent.
 */
final class Correspondance {

    private static final int LONGUEUR_MAX = 256;
    private static final int SEGMENTS_MAX = 16;
    private static final String TOUS = "*";

    private Correspondance() {}

    /**
     * @throws IllegalArgumentException si l'expression n'est pas lisible
     */
    static void verifier(String expression) {
        segments(expression);
    }

    /** La fiche traduite : uniquement les champs de la correspondance. */
    static ObjectNode traduire(JsonNode personne, Map<String, String> correspondance) {
        ObjectNode fiche = JsonNodeFactory.instance.objectNode();
        correspondance.forEach((nom, expression) -> {
            JsonNode valeur = lire(personne, expression);
            if (valeur != null) {
                fiche.set(nom, valeur);
            }
        });
        return fiche;
    }

    /** La valeur à cet emplacement, ou {@code null} s'il n'existe pas (ou vaut {@code null}). */
    static JsonNode lire(JsonNode document, String expression) {
        List<String> segments = segments(expression);
        List<JsonNode> trouvees = new ArrayList<>();
        parcourir(document, segments, 0, trouvees);
        if (segments.contains(TOUS)) {
            ArrayNode liste = JsonNodeFactory.instance.arrayNode();
            trouvees.forEach(liste::add);
            return liste;
        }
        return trouvees.isEmpty() ? null : trouvees.getFirst();
    }

    private static void parcourir(
            JsonNode noeud, List<String> segments, int rang, List<JsonNode> trouvees) {
        if (noeud == null || noeud.isNull() || noeud.isMissingNode()) {
            return;
        }
        if (rang == segments.size()) {
            trouvees.add(noeud);
            return;
        }
        String segment = segments.get(rang);
        if (segment.equals(TOUS)) {
            if (noeud.isArray()) {
                noeud.values().forEach(element -> parcourir(element, segments, rang + 1, trouvees));
            }
        } else if (noeud.isArray() && segment.matches("[0-9]{1,6}")) {
            parcourir(noeud.get(Integer.parseInt(segment)), segments, rang + 1, trouvees);
        } else if (noeud.isObject()) {
            parcourir(noeud.get(segment), segments, rang + 1, trouvees);
        }
    }

    /** Découpe l'expression ; la racine du document s'écrit « / » ou « . ». */
    static List<String> segments(String expression) {
        if (expression == null || expression.isEmpty() || expression.length() > LONGUEUR_MAX) {
            throw new IllegalArgumentException("emplacement vide ou trop long");
        }
        List<String> segments = new ArrayList<>();
        if (expression.equals("/") || expression.equals(".")) {
            return segments;
        }
        if (expression.startsWith("/")) {
            for (String segment : expression.substring(1).split("/", -1)) {
                // « [*] » est admis dans un pointeur par commodité ; « ~1 » et « ~0 » suivent la
                // RFC 6901.
                segments.add(
                        segment.equals("[*]")
                                ? TOUS
                                : segment.replace("~1", "/").replace("~0", "~"));
            }
        } else {
            for (String partie : expression.split("\\.", -1)) {
                if (!partie.matches("[^\\[\\]]+(\\[(\\*|[0-9]{1,6})\\])*")) {
                    throw new IllegalArgumentException("emplacement illisible : " + expression);
                }
                int crochet = partie.indexOf('[');
                segments.add(crochet < 0 ? partie : partie.substring(0, crochet));
                if (crochet >= 0) {
                    for (String indice : partie.substring(crochet + 1).split("\\]\\[?")) {
                        if (!indice.isEmpty()) {
                            segments.add(indice);
                        }
                    }
                }
            }
        }
        if (segments.size() > SEGMENTS_MAX || segments.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("emplacement illisible : " + expression);
        }
        return segments;
    }
}
