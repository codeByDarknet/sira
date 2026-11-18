package bf.sira.commun.donnees;

import java.util.List;

/** Forme commune de toute liste rendue par l'API. */
public record Page<T>(List<T> elements, int page, int taille, long total) {}
