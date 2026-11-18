package bf.sira.commun.pki;

/** Les usages d'une paire de clés hors transport. Une clé ne sert jamais à deux usages. */
public enum UsageCle {
    /** Signature des requêtes et des attestations (keyUsage : digitalSignature). */
    CACHET(0),
    /** Réception des attestations chiffrées (keyUsage : keyAgreement). */
    CHIFFREMENT(4);

    private final int bitKeyUsage;

    UsageCle(int bitKeyUsage) {
        this.bitKeyUsage = bitKeyUsage;
    }

    int bitKeyUsage() {
        return bitKeyUsage;
    }
}
