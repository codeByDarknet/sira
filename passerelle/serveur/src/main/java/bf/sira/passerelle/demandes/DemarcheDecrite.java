package bf.sira.passerelle.demandes;

import java.util.List;

/** Une démarche telle que la lit le service habilité : ce qui sera demandé au citoyen. */
public record DemarcheDecrite(String code, String libelle, List<JeuDemande> jeux) {}
