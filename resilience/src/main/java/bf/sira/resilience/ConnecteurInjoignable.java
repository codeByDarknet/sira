package bf.sira.resilience;

/** Panne passagère d'un connecteur : l'élément reste à traiter et sera redemandé. */
public class ConnecteurInjoignable extends RuntimeException {

    public ConnecteurInjoignable(String message) {
        super(message);
    }

    public ConnecteurInjoignable(String message, Throwable cause) {
        super(message, cause);
    }
}
