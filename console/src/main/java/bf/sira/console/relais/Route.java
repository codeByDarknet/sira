package bf.sira.console.relais;

import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/**
 * Une route relayée vers la passerelle : une méthode, un chemin dont les parties variables n'ont
 * qu'une forme possible, et l'accès exigé. Tout caractère inattendu (barre, encodage, « .. ») fait
 * échouer la correspondance : le chemin relayé ne peut pas sortir du préfixe prévu.
 */
record Route(HttpMethod methode, Pattern motif, Acces acces) {

    private static final String UUID = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    /** Un identifiant est un UUID (contrat §7) ou un numéro de ligne. */
    private static final String IDENTIFIANT = "(?:" + UUID + "|[0-9]{1,19})";

    /** Un code métier : {@code etat-civil.majeur}, {@code concours-direct}. */
    private static final String CODE = "[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?";

    /**
     * @param gabarit chemin sous {@code /api}, où {@code {id}} désigne un identifiant et {@code
     *     {code}} un code métier
     */
    static Route de(HttpMethod methode, String gabarit, Acces acces) {
        String motif = gabarit.replace("{id}", IDENTIFIANT).replace("{code}", CODE);
        return new Route(methode, Pattern.compile(motif), acces);
    }

    boolean accepte(HttpMethod methodeRecue, String chemin) {
        return methode.equals(methodeRecue) && motif.matcher(chemin).matches();
    }
}
