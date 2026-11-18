package bf.sira.demo.service.demandes;

/** Qui a ouvert la demande. */
public enum Origine {
    /** Le citoyen, depuis l'écran de la démarche. */
    CITOYEN,
    /** Un agent, qui redemande une vérification pour un dossier existant. */
    AGENT
}
