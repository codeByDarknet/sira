package bf.sira.demo.registre.personnes;

import bf.sira.demo.registre.configuration.ProprietesRegistre;
import bf.sira.demo.registre.configuration.ProprietesRegistre.Champ;
import bf.sira.demo.registre.configuration.SaisieRefusee;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Les règles d'une fiche de ce registre : ce qu'on peut y corriger, et comment on l'écrit. */
@Component
public class Fiches {

    private static final Pattern TEXTE = Pattern.compile("[\\p{L}\\p{M}\\p{N} '’.,/()°-]{1,100}");
    private static final LocalDate DATE_MINIMALE = LocalDate.of(1900, 1, 1);
    private static final DateTimeFormatter EN_TOUTES_LETTRES =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final String VIDE = "—";

    private final ProprietesRegistre registre;
    private final Clock horloge;

    Fiches(ProprietesRegistre registre, Clock horloge) {
        this.registre = registre;
        this.horloge = horloge;
    }

    /**
     * Contrôle une correction saisie par un agent et rend les valeurs à écrire.
     *
     * @throws SaisieRefusee si un champ est inconnu, non modifiable ou mal rempli
     */
    Map<String, Object> controler(Map<String, Object> champs) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        Map<String, String> erreurs = new LinkedHashMap<>();
        champs.forEach((nom, valeur) -> {
            Champ champ = registre.champ(nom).filter(Champ::modifiable).orElse(null);
            if (champ == null) {
                // Le nom vient de l'appelant : il n'est pas renvoyé.
                erreurs.put("champs", "Un des champs envoyés ne peut pas être modifié.");
                return;
            }
            String refus = refus(champ, valeur);
            if (refus == null) {
                valeurs.put(nom, valeur instanceof String texte ? texte.strip() : valeur);
            } else {
                erreurs.put(nom, refus);
            }
        });
        if (!erreurs.isEmpty()) {
            throw new SaisieRefusee(erreurs);
        }
        return valeurs;
    }

    /**
     * Contrôle une nouvelle fiche saisie par un agent : tous les champs du registre, sauf
     * l'identifiant, avec les mêmes règles qu'une correction.
     *
     * @throws SaisieRefusee si un champ est inconnu, manquant ou mal rempli
     */
    Map<String, Object> controlerCreation(Map<String, Object> champs) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        Map<String, String> erreurs = new LinkedHashMap<>();
        if (champs.keySet().stream()
                .anyMatch(
                        nom -> registre.champ(nom).isEmpty() || Personne.IDENTIFIANT.equals(nom))) {
            erreurs.put("champs", "Un des champs envoyés n’existe pas dans ce registre.");
        }
        for (Champ champ : registre.champs()) {
            if (Personne.IDENTIFIANT.equals(champ.nom())) {
                continue;
            }
            Object valeur = champs.get(champ.nom());
            String refus = refus(champ, valeur);
            if (refus != null) {
                erreurs.put(champ.nom(), refus);
            } else if (valeur != null && !(valeur instanceof String t && t.isBlank())) {
                valeurs.put(champ.nom(), valeur instanceof String texte ? texte.strip() : valeur);
            }
        }
        if (!erreurs.isEmpty()) {
            throw new SaisieRefusee(erreurs);
        }
        return valeurs;
    }

    private String refus(Champ champ, Object valeur) {
        if (valeur == null || valeur instanceof String texte && texte.isBlank()) {
            return champ.facultatif() ? null : "Ce champ est obligatoire.";
        }
        return switch (champ.type()) {
            case TEXTE ->
                    valeur instanceof String texte && TEXTE.matcher(texte.strip()).matches()
                            ? null
                            : "Saisissez au plus 100 caractères : lettres, chiffres et ponctuation"
                                    + " simple.";
            case DATE -> refusDate(valeur);
            case NOMBRE ->
                    valeur instanceof Number nombre
                                    && nombre.doubleValue() >= champ.minimum()
                                    && nombre.doubleValue() <= champ.maximum()
                            ? null
                            : "Saisissez un nombre entre %s et %s."
                                    .formatted(nombre(champ.minimum()), nombre(champ.maximum()));
            case BOOLEEN -> valeur instanceof Boolean ? null : "Répondez par oui ou par non.";
            case CHOIX ->
                    valeur instanceof String choix && champ.choix().containsKey(choix)
                            ? null
                            : "Choisissez une des valeurs proposées.";
        };
    }

    private String refusDate(Object valeur) {
        try {
            LocalDate date = LocalDate.parse(String.valueOf(valeur));
            return date.isBefore(DATE_MINIMALE) || date.isAfter(LocalDate.now(horloge))
                    ? "Saisissez une date entre 1900 et aujourd’hui."
                    : null;
        } catch (DateTimeParseException e) {
            return "Saisissez une date valide.";
        }
    }

    /** La valeur telle qu'un document l'imprime : date en toutes lettres, libellé d'un choix. */
    public String afficher(Champ champ, Object valeur) {
        if (valeur == null) {
            return VIDE;
        }
        return switch (champ.type()) {
            case DATE -> date(LocalDate.parse(String.valueOf(valeur)));
            case BOOLEEN -> Boolean.TRUE.equals(valeur) ? "Oui" : "Non";
            case CHOIX -> champ.choix().getOrDefault(String.valueOf(valeur), VIDE);
            case NOMBRE -> valeur instanceof Number n ? nombre(n.doubleValue()) : VIDE;
            case TEXTE -> String.valueOf(valeur);
        };
    }

    public static String date(LocalDate date) {
        String texte = EN_TOUTES_LETTRES.format(date);
        return date.getDayOfMonth() == 1 ? texte.replaceFirst("^1 ", "1er ") : texte;
    }

    private static String nombre(double valeur) {
        return BigDecimal.valueOf(valeur).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * L'identifiant d'une fiche importée.
     *
     * @throws IllegalStateException si la fiche n'a pas d'identifiant plausible ou de nom
     */
    String identifiantDe(Map<String, Object> fiche) {
        if (fiche.get(Personne.IDENTIFIANT) instanceof String identifiant
                && Personne.identifiantPlausible(identifiant)
                && fiche.get(Personne.NOM) instanceof String
                && fiche.get(Personne.PRENOMS) instanceof String) {
            return identifiant;
        }
        throw new IllegalStateException("fiche sans identifiant, nom ou prénoms dans l'import");
    }
}
