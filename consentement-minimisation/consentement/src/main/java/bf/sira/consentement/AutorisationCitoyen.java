package bf.sira.consentement;

import bf.sira.consentement.Vues.JeuVu;
import bf.sira.consentement.Vues.ServiceVu;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un accès que le citoyen a ouvert à un service (API, « Espace citoyen »).
 *
 * @param etat {@code ACTIVE}, {@code RETIREE} ou {@code EXPIREE}
 * @param consultations nombre de fois où une information a été remise au titre de cet accord
 */
public record AutorisationCitoyen(
        UUID id,
        String etat,
        Instant accordeeLe,
        Instant expireLe,
        Instant retireeLe,
        ServiceVu service,
        DemarcheVue demarche,
        List<JeuVu> jeux,
        long consultations,
        Instant derniereConsultationLe) {

    public record DemarcheVue(String code, String libelle, String finalite) {}
}
