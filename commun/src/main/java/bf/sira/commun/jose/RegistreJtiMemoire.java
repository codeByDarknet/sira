package bf.sira.commun.jose;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Registre en mémoire, borné. Il suffit pour une instance unique ; plusieurs instances d'un même
 * composant doivent partager un registre (base de données).
 */
public final class RegistreJtiMemoire implements RegistreJti {

    public static final int CAPACITE_PAR_DEFAUT = 100_000;

    private final Map<String, Instant> vus = new HashMap<>();
    private final int capacite;
    private final Clock horloge;

    public RegistreJtiMemoire() {
        this(CAPACITE_PAR_DEFAUT, Clock.systemUTC());
    }

    public RegistreJtiMemoire(int capacite, Clock horloge) {
        if (capacite < 1) {
            throw new IllegalArgumentException("capacité invalide");
        }
        this.capacite = capacite;
        this.horloge = horloge;
    }

    @Override
    public synchronized boolean consommer(String cle, Instant expiration) {
        Instant maintenant = horloge.instant();
        if (vus.size() >= capacite) {
            vus.values().removeIf(echeance -> !echeance.isAfter(maintenant));
        }
        // Registre plein d'identifiants encore valides : on refuse plutôt que d'en oublier un,
        // car l'oubli rouvrirait la porte au rejeu.
        if (vus.size() >= capacite) {
            return false;
        }
        Instant precedente = vus.get(cle);
        if (precedente != null && precedente.isAfter(maintenant)) {
            return false;
        }
        vus.put(cle, expiration);
        return true;
    }
}
