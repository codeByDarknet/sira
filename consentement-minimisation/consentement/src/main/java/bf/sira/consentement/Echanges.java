package bf.sira.consentement;

import java.util.List;
import java.util.UUID;

/**
 * Ce que le consentement attend de la passerelle d'échange. Le module ne connaît ni les registres
 * ni le référentiel : il annonce un accord ou un retrait, la passerelle fait le reste. Elle fournit
 * l'implémentation ({@code bf.sira.passerelle.demandes.EchangesPasserelle}).
 */
public interface Echanges {

    /**
     * Les jeux pour lesquels ce service est habilité dans cette démarche, aujourd'hui ; liste vide
     * si l'habilitation a été suspendue.
     */
    List<String> jeuxHabilites(String service, String demarche);

    /** Inscrit ce qu'il faut obtenir pour une demande acceptée, dans la transaction de l'accord. */
    void preparer(UUID demande);

    /** Interroge aussitôt les registres, hors transaction, dans la limite du temps accordé. */
    void essayerMaintenant(UUID demande);

    /** Recalcule l'état d'une demande dont des éléments viennent d'être abandonnés. */
    void actualiser(UUID demande);
}
