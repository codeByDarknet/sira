package bf.sira.connecteur.regles;

import bf.sira.connecteur.Refus;
import bf.sira.connecteur.Refus.Motif;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import tools.jackson.databind.JsonNode;

/** Lecture des dates d'une fiche : {@code 2008-11-16} ou {@code 2008-11-16T00:00:00Z}. */
final class Dates {

    private static final int LONGUEUR_MAX = 40;

    private Dates() {}

    static boolean estUneDate(JsonNode valeur) {
        return valeur.isString() && analyser(valeur.stringValue(), ZoneId.of("UTC")) != null;
    }

    /**
     * @throws Refus si la valeur n'est pas une date ; l'erreur ne cite jamais la valeur
     */
    static LocalDate lire(JsonNode valeur, ZoneId fuseau) {
        LocalDate date = valeur.isString() ? analyser(valeur.stringValue(), fuseau) : null;
        if (date == null) {
            throw new Refus(Motif.REGLE_INAPPLICABLE);
        }
        return date;
    }

    private static LocalDate analyser(String texte, ZoneId fuseau) {
        if (texte.length() > LONGUEUR_MAX) {
            return null;
        }
        try {
            return texte.length() == 10
                    ? LocalDate.parse(texte)
                    : OffsetDateTime.parse(texte).atZoneSameInstant(fuseau).toLocalDate();
        } catch (DateTimeException e) {
            // Le message de l'exception cite le texte lu : il n'est ni propagé ni journalisé.
            return null;
        }
    }
}
