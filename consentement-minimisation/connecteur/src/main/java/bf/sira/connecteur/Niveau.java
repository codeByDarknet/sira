package bf.sira.connecteur;

/** Forme sous laquelle une donnée quitte le registre, de la moins à la plus révélatrice. */
public enum Niveau {
    /** Une réponse par oui ou par non. */
    STATUT,
    /** La valeur de quelques champs. */
    CHAMPS,
    /** Une pièce complète. */
    DOCUMENT
}
