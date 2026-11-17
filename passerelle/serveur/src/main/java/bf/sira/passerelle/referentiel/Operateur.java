package bf.sira.passerelle.referentiel;

/** Les opérateurs d'une règle de niveau STATUT (contrat §7.3). Le connecteur les évalue. */
public enum Operateur {
    EGAL,
    DIFFERENT,
    AU_MOINS,
    AU_PLUS,
    AGE_AU_MOINS,
    AGE_AU_PLUS,
    EXISTE,
    AUCUN,
    ENCORE_VALIDE
}
