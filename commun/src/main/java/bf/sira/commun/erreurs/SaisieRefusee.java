package bf.sira.commun.erreurs;

/**
 * Une saisie que la validation de forme ne suffit pas à juger (référence inconnue, champs
 * incohérents entre eux). Le message s'adresse à la personne qui saisit ; il ne cite aucune valeur.
 */
public class SaisieRefusee extends RuntimeException {

    private final String champ;

    /**
     * @param champ nom du champ dans le JSON reçu
     */
    public SaisieRefusee(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String champ() {
        return champ;
    }
}
