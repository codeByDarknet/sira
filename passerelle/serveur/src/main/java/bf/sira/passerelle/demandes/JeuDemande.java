package bf.sira.passerelle.demandes;

import bf.sira.passerelle.referentiel.Jeu;
import bf.sira.passerelle.referentiel.Niveau;

/** Un jeu tel que le service le lit dans sa demande : ce qui sera demandé, et à quel registre. */
public record JeuDemande(String code, String libelle, Niveau niveau, String registre) {

    static JeuDemande de(Jeu jeu) {
        return new JeuDemande(jeu.code(), jeu.libelle(), jeu.niveau(), jeu.registre());
    }
}
