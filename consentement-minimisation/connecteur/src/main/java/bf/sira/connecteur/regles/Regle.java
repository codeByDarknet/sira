package bf.sira.connecteur.regles;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import tools.jackson.databind.JsonNode;

/**
 * Une question de niveau STATUT : un champ, un opérateur et, selon l'opérateur, une valeur de
 * comparaison.
 *
 * @param valeur {@code null} pour les opérateurs sans valeur
 */
public record Regle(Chemin champ, Operateur operateur, JsonNode valeur) {

    private static final int AGE_MAX = 150;
    private static final int LONGUEUR_TEXTE_MAX = 256;

    /**
     * @throws Refus si la valeur ne convient pas à l'opérateur
     */
    public static Regle de(String champ, Operateur operateur, JsonNode valeur) {
        JsonNode v = valeur == null || valeur.isNull() ? null : valeur;
        boolean valide =
                operateur != null
                        && switch (operateur) {
                            case EXISTE, AUCUN, ENCORE_VALIDE -> v == null;
                            case AGE_AU_MOINS, AGE_AU_PLUS ->
                                    v != null
                                            && v.isIntegralNumber()
                                            && v.canConvertToInt()
                                            && v.intValue() >= 0
                                            && v.intValue() <= AGE_MAX;
                            case AU_MOINS, AU_PLUS ->
                                    v != null && (v.isNumber() || Dates.estUneDate(v));
                            case EGAL, DIFFERENT ->
                                    v != null && (v.isNumber() || v.isBoolean() || texteCourt(v));
                        };
        if (!valide) {
            throw new Refus(Motif.JEU_INVALIDE);
        }
        return new Regle(Chemin.lire(champ), operateur, v);
    }

    private static boolean texteCourt(JsonNode valeur) {
        return valeur.isString() && valeur.stringValue().length() <= LONGUEUR_TEXTE_MAX;
    }

    /** Égalité de deux valeurs simples ; les nombres sont comparés sans égard à leur écriture. */
    public static boolean memeValeur(JsonNode a, JsonNode b) {
        return a.isNumber() && b.isNumber()
                ? a.decimalValue().compareTo(b.decimalValue()) == 0
                : a.equals(b);
    }
}
