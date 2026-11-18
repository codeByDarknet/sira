package bf.sira.passerelle.demandes;

import bf.sira.consentement.Echanges;
import bf.sira.passerelle.collecte.Collecte;
import bf.sira.passerelle.referentiel.Habilitations;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ce que le module de consentement attend de la passerelle : les habilitations du référentiel et la
 * collecte auprès des registres. Le module ne dépend ainsi que de cette interface.
 */
@Component
class EchangesPasserelle implements Echanges {

    private final Collecte collecte;
    private final Habilitations habilitations;
    private final CollectesApresAccord apresAccord;

    EchangesPasserelle(
            Collecte collecte, Habilitations habilitations, CollectesApresAccord apresAccord) {
        this.collecte = collecte;
        this.habilitations = habilitations;
        this.apresAccord = apresAccord;
    }

    @Override
    public List<String> jeuxHabilites(String service, String demarche) {
        return habilitations.jeuxHabilites(service, demarche);
    }

    @Override
    public void preparer(UUID demande) {
        collecte.preparer(demande);
    }

    @Override
    public void essayerMaintenant(UUID demande) {
        // L'accord répond tout de suite : les registres sont interrogés à côté.
        apresAccord.lancer(demande);
    }

    @Override
    public void actualiser(UUID demande) {
        collecte.actualiser(demande);
    }
}
