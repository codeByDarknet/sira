package bf.sira.connecteur.regles;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import tools.jackson.databind.JsonNode;

/**
 * Applique à une fiche les règles reçues : aucune règle n'est écrite en code, toutes viennent de la
 * définition du jeu.
 *
 * <p>Quand un chemin désigne plusieurs valeurs (il traverse une liste), une comparaison est vraie
 * dès qu'une valeur la satisfait. Un champ absent ne satisfait aucune comparaison. Une valeur d'un
 * type inattendu (un texte là où la règle attend une date) fait refuser la règle : le registre
 * n'atteste pas ce qu'il ne sait pas lire.
 */
public final class MoteurRegles {

    private final Clock horloge;
    private final ZoneId fuseau;

    public MoteurRegles(Clock horloge, ZoneId fuseau) {
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    /** Niveau STATUT : la réponse par oui ou par non, seule chose qui sortira du connecteur. */
    public boolean evaluer(Regle regle, JsonNode fiche) {
        List<JsonNode> valeurs = regle.champ().resoudre(fiche).valeurs();
        JsonNode attendu = regle.valeur();
        LocalDate aujourdhui = LocalDate.now(horloge.withZone(fuseau));
        return switch (regle.operateur()) {
            case EXISTE -> !valeurs.isEmpty();
            case AUCUN -> valeurs.isEmpty();
            case EGAL -> valeurs.stream().anyMatch(v -> egal(v, attendu));
            // « Différent de » : la valeur existe, et aucune n'est égale à celle attendue.
            case DIFFERENT ->
                    !valeurs.isEmpty() && valeurs.stream().noneMatch(v -> egal(v, attendu));
            case AU_MOINS -> auMoinsUne(valeurs, v -> comparer(v, attendu) >= 0);
            case AU_PLUS -> auMoinsUne(valeurs, v -> comparer(v, attendu) <= 0);
            case AGE_AU_MOINS -> auMoinsUn(valeurs, aujourdhui, age -> age >= attendu.intValue());
            case AGE_AU_PLUS -> auMoinsUn(valeurs, aujourdhui, age -> age <= attendu.intValue());
            case ENCORE_VALIDE ->
                    auMoinsUne(valeurs, v -> !Dates.lire(v, fuseau).isBefore(aujourdhui));
        };
    }

    /**
     * Niveau CHAMPS : la valeur de chaque champ demandé. Un champ absent est omis ; un chemin qui
     * traverse une liste donne une liste. Seules des valeurs simples sortent, jamais un objet
     * entier.
     */
    public Map<String, Object> extraire(List<Chemin> champs, JsonNode fiche) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        for (Chemin champ : champs) {
            Chemin.Selection selection = champ.resoudre(fiche);
            List<Object> valeurs = selection.valeurs().stream().map(MoteurRegles::simple).toList();
            if (selection.liste()) {
                resultat.put(champ.texte(), valeurs);
            } else if (!valeurs.isEmpty()) {
                resultat.put(champ.texte(), valeurs.getFirst());
            }
        }
        return resultat;
    }

    private interface Condition {
        boolean satisfaite(JsonNode valeur);
    }

    // Toutes les valeurs sont examinées : une valeur illisible fait refuser la règle même si une
    // autre la satisfait, et la durée du calcul ne dépend pas de la première valeur satisfaisante.
    private static boolean auMoinsUne(List<JsonNode> valeurs, Condition condition) {
        boolean trouvee = false;
        for (JsonNode valeur : valeurs) {
            trouvee |= condition.satisfaite(valeur);
        }
        return trouvee;
    }

    private boolean auMoinsUn(List<JsonNode> valeurs, LocalDate aujourdhui, IntPredicate age) {
        return auMoinsUne(valeurs, v -> age.test(age(Dates.lire(v, fuseau), aujourdhui)));
    }

    /** Âge en années révolues : une personne née un 29 février vieillit le 1er mars. */
    private static int age(LocalDate naissance, LocalDate aujourdhui) {
        if (naissance.isAfter(aujourdhui)) {
            throw new Refus(Motif.REGLE_INAPPLICABLE);
        }
        return Period.between(naissance, aujourdhui).getYears();
    }

    private static boolean egal(JsonNode valeur, JsonNode attendu) {
        boolean memeType =
                attendu.isNumber()
                        ? valeur.isNumber()
                        : attendu.isBoolean() ? valeur.isBoolean() : valeur.isString();
        if (!memeType) {
            throw new Refus(Motif.REGLE_INAPPLICABLE);
        }
        return Regle.memeValeur(valeur, attendu);
    }

    private int comparer(JsonNode valeur, JsonNode attendu) {
        if (attendu.isNumber()) {
            if (!valeur.isNumber()) {
                throw new Refus(Motif.REGLE_INAPPLICABLE);
            }
            return valeur.decimalValue().compareTo(attendu.decimalValue());
        }
        return Dates.lire(valeur, fuseau).compareTo(Dates.lire(attendu, fuseau));
    }

    private static Object simple(JsonNode valeur) {
        if (valeur.isString()) {
            return valeur.stringValue();
        }
        if (valeur.isBoolean()) {
            return valeur.booleanValue();
        }
        if (valeur.isNumber()) {
            return valeur.numberValue();
        }
        throw new Refus(Motif.REGLE_INAPPLICABLE);
    }
}
