package bf.sira.connecteur.regles;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Désigne un champ de la fiche d'un registre : des noms séparés par un point, chacun pouvant porter
 * un filtre d'égalité, par exemple {@code date_naissance}, {@code adresse.commune} ou {@code
 * diplomes[type=BAC].mention}.
 *
 * <p>C'est une simple description de parcours, lue par une expression régulière : il n'y a ni
 * fonction, ni opérateur, ni évaluation d'expression. Une liste rencontrée en route est parcourue
 * élément par élément.
 */
public record Chemin(String texte, List<Segment> segments) {

    private static final int LONGUEUR_MAX = 128;
    private static final int SEGMENTS_MAX = 5;
    private static final String NOM = "[A-Za-z_][A-Za-z0-9_]{0,63}";
    private static final Pattern SEGMENT =
            Pattern.compile("(" + NOM + ")(?:\\[(" + NOM + ")=([\\p{L}\\p{N}_-]{1,64})])?");

    /**
     * @param cleFiltre champ comparé dans chaque élément, ou {@code null} sans filtre
     * @param valeurFiltre valeur que ce champ doit avoir
     */
    public record Segment(String nom, String cleFiltre, String valeurFiltre) {

        private boolean retient(JsonNode element) {
            return cleFiltre == null
                    || (element.isObject()
                            && valeurFiltre.equals(element.path(cleFiltre).asString("")));
        }
    }

    /**
     * Ce que le chemin désigne dans une fiche.
     *
     * @param valeurs les valeurs trouvées, sans les absentes ni les nulles
     * @param liste vrai si le chemin a traversé une liste : il peut désigner plusieurs valeurs
     */
    public record Selection(List<JsonNode> valeurs, boolean liste) {}

    /**
     * @throws Refus si le texte n'est pas un chemin
     */
    public static Chemin lire(String texte) {
        if (texte == null || texte.isEmpty() || texte.length() > LONGUEUR_MAX) {
            throw new Refus(Motif.JEU_INVALIDE);
        }
        String[] morceaux = texte.split("\\.", -1);
        if (morceaux.length > SEGMENTS_MAX) {
            throw new Refus(Motif.JEU_INVALIDE);
        }
        List<Segment> segments = new ArrayList<>();
        for (String morceau : morceaux) {
            Matcher m = SEGMENT.matcher(morceau);
            if (!m.matches()) {
                throw new Refus(Motif.JEU_INVALIDE);
            }
            segments.add(new Segment(m.group(1), m.group(2), m.group(3)));
        }
        return new Chemin(texte, List.copyOf(segments));
    }

    /** Le champ désigné, filtres retirés : c'est sous ce nom que la politique locale l'expose. */
    public String sansFiltres() {
        return String.join(".", segments.stream().map(Segment::nom).toList());
    }

    /** Les champs lus par les filtres : filtrer sur un champ, c'est déjà l'interroger. */
    public List<String> champsDesFiltres() {
        List<String> champs = new ArrayList<>();
        StringBuilder prefixe = new StringBuilder();
        for (Segment segment : segments) {
            prefixe.append(segment.nom()).append('.');
            if (segment.cleFiltre() != null) {
                champs.add(prefixe + segment.cleFiltre());
            }
        }
        return champs;
    }

    public Selection resoudre(JsonNode fiche) {
        List<JsonNode> courants = List.of(fiche);
        boolean liste = false;
        for (Segment segment : segments) {
            List<JsonNode> suivants = new ArrayList<>();
            for (JsonNode courant : courants) {
                JsonNode valeur = courant.isObject() ? courant.get(segment.nom()) : null;
                if (valeur == null || valeur.isNull()) {
                    continue;
                }
                if (valeur.isArray()) {
                    liste = true;
                    valeur.values().stream().filter(e -> !e.isNull()).forEach(suivants::add);
                } else {
                    suivants.add(valeur);
                }
            }
            suivants.removeIf(element -> !segment.retient(element));
            courants = suivants;
        }
        return new Selection(courants, liste);
    }
}
