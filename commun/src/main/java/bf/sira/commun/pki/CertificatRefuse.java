package bf.sira.commun.pki;

/**
 * Un certificat présenté ne remplit pas une condition de confiance. Le message ne contient aucun
 * secret.
 */
public class CertificatRefuse extends RuntimeException {

    public CertificatRefuse(String message) {
        super(message);
    }

    public CertificatRefuse(String message, Throwable cause) {
        super(message, cause);
    }
}
