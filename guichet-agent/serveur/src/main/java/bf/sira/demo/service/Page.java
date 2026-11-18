package bf.sira.demo.service;

import java.util.List;

/** Une page de liste, dans la forme commune à toutes les API de Sira. */
public record Page<T>(List<T> elements, int page, int taille, long total) {

    public static final int TAILLE_MAX = 100;
    public static final int PAGE_MAX = 100_000;

    public static long decalage(int page, int taille) {
        return (long) (page - 1) * taille;
    }
}
