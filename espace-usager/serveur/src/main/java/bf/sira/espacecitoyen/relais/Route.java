package bf.sira.espacecitoyen.relais;

import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/**
 * Une route relayée vers la passerelle : une méthode et un chemin, dont les parties variables n'ont
 * qu'une forme possible. Tout caractère inattendu (point, barre, encodage) fait échouer la
 * correspondance : le chemin relayé ne peut pas sortir du préfixe prévu.
 */
record Route(HttpMethod methode, Pattern motif) {

    private static final String UUID = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    /** Un identifiant est un UUID (contrat §7) ou un numéro de ligne. */
    private static final String IDENTIFIANT = "(?:" + UUID + "|[0-9]{1,19})";

    /**
     * @param gabarit chemin sous {@code /api}, où {@code {id}} désigne un identifiant
     */
    static Route de(HttpMethod methode, String gabarit) {
        return new Route(methode, Pattern.compile(gabarit.replace("{id}", IDENTIFIANT)));
    }

    boolean accepte(HttpMethod methodeRecue, String chemin) {
        return methode.equals(methodeRecue) && motif.matcher(chemin).matches();
    }
}
