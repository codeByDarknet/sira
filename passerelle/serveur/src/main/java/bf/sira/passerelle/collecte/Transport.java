package bf.sira.passerelle.collecte;

import bf.sira.resilience.ConnecteurInjoignable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le chemin qu'emprunte une requête signée pour atteindre un connecteur (contrat §12). Ce qui
 * protège l'échange (signature de la requête, chiffrement de l'attestation) ne dépend pas du
 * transport : ajouter un transport, c'est déclarer un autre composant de ce type.
 */
public interface Transport {

    /** La valeur du champ {@code transport} des registres que ce transport dessert. */
    String code();

    /**
     * @param registre code du registre : le connecteur joint doit en porter l'identité ({@code
     *     urn:sira:registre:<code>}), sinon rien ne lui est envoyé
     * @param adresse adresse du connecteur, telle que déclarée pour le registre
     * @param requeteSignee JWS compact (contrat §8)
     * @throws ConnecteurInjoignable si le connecteur ne répond pas, n'est pas celui du registre, ou
     *     répond par une panne
     */
    Reponse envoyer(String registre, String adresse, String requeteSignee);

    /**
     * Le schéma publié par le connecteur (champs et documents exposés). Un transport qui ne sait
     * pas le lire le dit par {@link ConnecteurInjoignable}.
     *
     * @throws ConnecteurInjoignable si le connecteur ne répond pas ou n'est pas celui du registre
     */
    default String schema(String registre, String adresse) {
        throw new ConnecteurInjoignable("schéma indisponible par ce transport");
    }

    /**
     * @param statut statut HTTP rendu par le connecteur
     * @param attestation JWE compact, présent si le statut est 200
     */
    record Reponse(int statut, String attestation) {}

    /** Les transports disponibles, rangés par la valeur que les registres déclarent. */
    static Map<String, Transport> parCode(List<Transport> transports) {
        return transports.stream().collect(Collectors.toMap(Transport::code, Function.identity()));
    }
}
