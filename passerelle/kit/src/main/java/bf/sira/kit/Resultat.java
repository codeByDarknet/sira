package bf.sira.kit;

import java.util.Map;

/** Ce qu'un registre atteste, sous l'une des trois formes du contrat §8. */
public sealed interface Resultat {

    /** Niveau {@code STATUT} : une réponse par oui ou par non. */
    record Statut(boolean valeur) implements Resultat {}

    /** Niveau {@code CHAMPS} : des champs nommés. */
    record Champs(Map<String, Object> champs) implements Resultat {

        public Champs {
            champs = Map.copyOf(champs);
        }
    }

    /**
     * Niveau {@code DOCUMENT} : une pièce, dont l'empreinte a été vérifiée.
     *
     * @param type type MIME annoncé par le registre
     */
    record Document(String nom, String type, byte[] contenu) implements Resultat {}
}
