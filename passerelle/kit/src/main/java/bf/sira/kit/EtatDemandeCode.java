package bf.sira.kit;

/** États d'une demande (contrat §6). */
public enum EtatDemandeCode {
    EN_ATTENTE_ACCORD,
    REFUSEE,
    EXPIREE,
    ANNULEE,
    EN_COURS,
    PARTIELLE,
    COMPLETE;

    /** Vrai quand la demande n'évoluera plus. */
    public boolean estFinal() {
        return this == REFUSEE || this == EXPIREE || this == ANNULEE || this == COMPLETE;
    }
}
