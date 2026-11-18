package bf.sira.commun.jose;

import java.time.Instant;

/** Mémoire des identifiants d'échange déjà vus, pour refuser tout rejeu. */
public interface RegistreJti {

    /**
     * Consomme un identifiant.
     *
     * @param cle identifiant, qualifié par son émetteur
     * @param expiration instant après lequel l'objet signé n'est plus accepté de toute façon
     * @return {@code false} si l'identifiant a déjà été vu, ou s'il ne peut pas être mémorisé
     */
    boolean consommer(String cle, Instant expiration);
}
