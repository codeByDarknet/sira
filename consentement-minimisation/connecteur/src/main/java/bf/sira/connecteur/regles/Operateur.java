package bf.sira.connecteur.regles;

/** Les huit questions qu'une règle de niveau STATUT peut poser sur un champ (contrat §7.3). */
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
