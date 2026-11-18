package bf.sira.journal;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Ce que le journal attend de qui connaît les citoyens. Le journal ne garde jamais l'identifiant
 * unique d'une personne : il ne voit que son pseudonyme propre au journal. La passerelle fournit
 * l'implémentation ({@code bf.sira.consentement.Citoyens}).
 */
public interface CitoyensJournal {

    /**
     * Le citoyen qui porte ce jeton, sous son pseudonyme interne.
     *
     * @throws org.springframework.security.access.AccessDeniedException si le jeton ne désigne
     *     personne
     */
    String courant(Jwt jeton);

    /** La désignation de ce citoyen (pseudonyme interne) dans le journal. */
    String pseudonymeJournal(String citoyen);
}
