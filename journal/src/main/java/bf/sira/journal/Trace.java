package bf.sira.journal;

import bf.sira.commun.jetons.Acteur;
import java.util.UUID;

/**
 * Ce qu'il faut écrire au journal. Le citoyen y est donné par son pseudonyme interne ; le journal
 * le remplace par le pseudonyme du secteur {@code journal}.
 */
public final class Trace {

    final TypeLigne type;
    final Resultat resultat;
    String citoyen;
    String service;
    String registre;
    String jeu;
    String niveau;
    String demarche;
    String finalite;
    String canal;
    String idEchange;
    UUID demande;
    UUID autorisation;
    String detail;
    String acteur;
    String objet;

    private Trace(TypeLigne type, Resultat resultat) {
        this.type = type;
        this.resultat = resultat;
    }

    public static Trace de(TypeLigne type, Resultat resultat) {
        return new Trace(type, resultat);
    }

    public Trace citoyen(String citoyen) {
        this.citoyen = citoyen;
        return this;
    }

    public Trace service(String service) {
        this.service = service;
        return this;
    }

    public Trace jeu(String registre, String jeu, String niveau) {
        this.registre = registre;
        this.jeu = jeu;
        this.niveau = niveau;
        return this;
    }

    public Trace demarche(String demarche, String finalite) {
        this.demarche = demarche;
        this.finalite = finalite;
        return this;
    }

    public Trace canal(String canal) {
        this.canal = canal;
        return this;
    }

    public Trace idEchange(String idEchange) {
        this.idEchange = idEchange;
        return this;
    }

    public Trace demande(UUID demande) {
        this.demande = demande;
        return this;
    }

    public Trace autorisation(UUID autorisation) {
        this.autorisation = autorisation;
        return this;
    }

    /** Un mot-clé qui précise l'issue ({@code EXPIRATION}, {@code PERSONNE_INCONNUE}…). */
    public Trace detail(String detail) {
        this.detail = detail;
        return this;
    }

    /** L'objet sur lequel porte la ligne, quand ce n'est pas un jeu : {@code membre:concours}. */
    public Trace objet(String objet) {
        this.objet = objet;
        return this;
    }

    /**
     * Une action d'administration.
     *
     * @param objet l'objet du référentiel touché : {@code membre:concours}
     * @param action ce qui a été fait : {@code CREATION}, {@code APPROBATION}…
     */
    public static Trace administration(Acteur acteur, String objet, String action) {
        Trace trace = new Trace(TypeLigne.ADMINISTRATION, Resultat.REUSSI);
        trace.acteur = acteur.nom();
        trace.objet = objet;
        trace.detail = action;
        return trace;
    }
}
