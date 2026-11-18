package bf.sira.kit;

/** États d'un élément de demande, c'est-à-dire d'un jeu de données attendu (contrat §6). */
public enum EtatElement {
    A_TRAITER,
    OBTENU,
    REUTILISE,
    /** Le registre refuse de répondre. */
    REFUSE,
    /** La personne est absente du registre. */
    INCONNU,
    EXPIRE;

    /** Vrai quand une attestation accompagne l'élément. */
    public boolean porteUneAttestation() {
        return this == OBTENU || this == REUTILISE;
    }
}
