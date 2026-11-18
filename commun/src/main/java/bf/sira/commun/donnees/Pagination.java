package bf.sira.commun.donnees;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Paramètres {@code ?page=&taille=} : toute liste est paginée et bornée (contrat §5). */
public record Pagination(@Min(1) @Max(1_000_000) Integer page, @Min(1) @Max(100) Integer taille) {

    public Pagination {
        page = page == null ? 1 : page;
        taille = taille == null ? 20 : taille;
    }

    public long decalage() {
        return (long) (page - 1) * taille;
    }
}
