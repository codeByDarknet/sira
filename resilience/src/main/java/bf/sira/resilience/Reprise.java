package bf.sira.resilience;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Ce que la file attend de la passerelle : retraiter des éléments qu'elle vient de réserver. La
 * passerelle le fournit ; la file ne connaît ni les registres ni les connecteurs.
 */
public interface Reprise {

    /**
     * @param elements identifiants des éléments réservés
     * @param bail durée de la réservation : le traitement ne doit pas la dépasser
     */
    void reprendre(List<UUID> elements, Duration bail);
}
