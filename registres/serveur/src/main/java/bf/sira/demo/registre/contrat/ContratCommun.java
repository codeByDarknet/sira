package bf.sira.demo.registre.contrat;

import bf.sira.demo.registre.personnes.Personne;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Projection d'une fiche du registre vers le contrat commun ({@code registres/openapi.yaml}). La
 * fiche stockée reste plate ; seule sa présentation sur {@code /v1/} suit le contrat. Un champ sans
 * valeur est omis, jamais rendu {@code null}.
 */
public final class ContratCommun {

    /** Registres que le contrat commun décrit ; les autres n'exposent que l'API interne. */
    public static final List<String> REGISTRES = List.of("etat-civil", "nationalite", "casier");

    /** Le détail d'une mention au casier n'est volontairement pas simulé. */
    static final String NATURE_NON_SIMULEE =
            "Condamnation inscrite au bulletin n° 3 (détail non simulé)";

    private ContratCommun() {}

    /** La fiche au format du contrat, ou rien si ce registre n'est pas décrit par le contrat. */
    public static Optional<Map<String, Object>> projeter(
            String registre, Map<String, Object> fiche) {
        return Optional.ofNullable(
                switch (registre) {
                    case "etat-civil" -> etatCivil(fiche);
                    case "nationalite" -> nationalite(fiche);
                    case "casier" -> casier(fiche);
                    default -> null;
                });
    }

    static Map<String, Object> etatCivil(Map<String, Object> fiche) {
        Map<String, Object> reponse = identite(fiche);
        mettre(reponse, "sexe", fiche.get("sexe"));
        mettre(
                reponse,
                "naissance",
                objet(
                        "date", fiche.get(Personne.DATE_NAISSANCE),
                        "lieu", fiche.get("lieu_naissance"),
                        "province", fiche.get("province_naissance"),
                        "pays", fiche.get("pays_naissance")));
        mettre(
                reponse,
                "filiation",
                objet("pere", fiche.get("nom_pere"), "mere", fiche.get("nom_mere")));
        mettre(
                reponse,
                "acte",
                objet("type", fiche.get("type_acte"), "numero", fiche.get("numero_acte")));
        return reponse;
    }

    static Map<String, Object> nationalite(Map<String, Object> fiche) {
        Map<String, Object> reponse = identite(fiche);
        Map<String, Object> nationalite =
                objet(
                        "code", fiche.get("nationalite"),
                        "mode_acquisition", fiche.get("mode_acquisition"),
                        "certificat",
                                fiche.get("numero_certificat") == null
                                        ? null
                                        : objet(
                                                "numero", fiche.get("numero_certificat"),
                                                "date", fiche.get("date_certificat")));
        reponse.put(
                "nationalites", nationalite.containsKey("code") ? List.of(nationalite) : List.of());
        return reponse;
    }

    static Map<String, Object> casier(Map<String, Object> fiche) {
        Map<String, Object> reponse = identite(fiche);
        mettre(
                reponse,
                "naissance",
                objet(
                        "date", fiche.get(Personne.DATE_NAISSANCE),
                        "lieu", fiche.get("lieu_naissance")));
        // Le registre simulé ne connaît que « néant » ou « comporte des mentions » : une mention
        // devient une condamnation générique, rattachée à la juridiction de la personne.
        reponse.put(
                "condamnations",
                "NEANT".equals(fiche.get("bulletin3"))
                        ? List.of()
                        : List.of(
                                objet(
                                        "juridiction",
                                        fiche.get("juridiction"),
                                        "nature",
                                        NATURE_NON_SIMULEE)));
        return reponse;
    }

    private static Map<String, Object> identite(Map<String, Object> fiche) {
        Map<String, Object> reponse = new LinkedHashMap<>();
        mettre(reponse, "identifiant", fiche.get(Personne.IDENTIFIANT));
        mettre(reponse, "nom", fiche.get(Personne.NOM));
        mettre(reponse, "prenoms", fiche.get(Personne.PRENOMS));
        return reponse;
    }

    /**
     * Un objet aux champs dans l'ordre donné (nom, valeur, nom, valeur…), sans les valeurs nulles.
     */
    private static Map<String, Object> objet(Object... nomsEtValeurs) {
        Map<String, Object> objet = new LinkedHashMap<>();
        for (int i = 0; i < nomsEtValeurs.length; i += 2) {
            mettre(objet, (String) nomsEtValeurs[i], nomsEtValeurs[i + 1]);
        }
        return objet;
    }

    private static void mettre(Map<String, Object> objet, String nom, Object valeur) {
        boolean vide =
                valeur == null
                        || (valeur instanceof String texte && texte.isBlank())
                        || (valeur instanceof Map<?, ?> sousObjet && sousObjet.isEmpty());
        if (!vide) {
            objet.put(nom, valeur);
        }
    }
}
