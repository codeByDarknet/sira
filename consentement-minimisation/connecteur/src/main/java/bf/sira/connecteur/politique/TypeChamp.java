package bf.sira.connecteur.politique;

/**
 * Nature d'un champ exposé, déclarée par l'administration dans sa politique. Elle dit à la console
 * quelles questions ont un sens : égal ou différent pour un texte, avant ou après pour une date (ou
 * un âge), au moins ou au plus pour un nombre.
 */
public enum TypeChamp {
    TEXTE,
    DATE,
    NOMBRE,
    BOOLEEN
}
