package bf.sira.commun.jetons;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * L'administrateur qui agit, lu dans son jeton du royaume {@code sira-socle}.
 *
 * @param id identifiant stable du compte : c'est lui qui fonde la séparation des rôles
 * @param nom nom affiché dans les dossiers et dans le journal
 */
public record Acteur(String id, String nom) {

    /** Acteur des écritures faites par le socle lui-même, au premier démarrage. */
    public static final Acteur AMORCAGE = new Acteur("amorcage", "Installation de Sira");

    public static Acteur de(Jwt jeton) {
        String nom = jeton.getClaimAsString("name");
        if (nom == null || nom.isBlank()) {
            nom = jeton.getClaimAsString("preferred_username");
        }
        return new Acteur(jeton.getSubject(), nom == null ? jeton.getSubject() : nom);
    }
}
