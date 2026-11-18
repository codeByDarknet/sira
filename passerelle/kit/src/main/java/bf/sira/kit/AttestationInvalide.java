package bf.sira.kit;

/** Une attestation authentique dont le contenu ne respecte pas le contrat. */
public class AttestationInvalide extends RuntimeException {

    public AttestationInvalide(String message) {
        super(message);
    }
}
