package bf.sira.kit;

import java.util.List;

/** Une démarche et les jeux que l'habilitation du service lui accorde. */
public record Demarche(String code, String libelle, List<Jeu> jeux) {}
