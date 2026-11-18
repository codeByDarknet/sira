package bf.sira.kit;

/** Les trois façons de recueillir l'accord du citoyen (contrat §6). */
public enum Canal {
    /** Le navigateur du citoyen est envoyé vers l'écran d'accord, puis revient chez le service. */
    REDIRECTION,
    /** Le service affiche un QR code et un code court, que le citoyen saisit dans son espace. */
    CODE,
    /** La demande apparaît directement dans l'espace du citoyen désigné. */
    NOTIFICATION
}
