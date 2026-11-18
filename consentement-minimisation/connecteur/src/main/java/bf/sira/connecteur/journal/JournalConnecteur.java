package bf.sira.connecteur.journal;

import bf.sira.connecteur.requetes.Requete;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le journal local du registre : qui a demandé quoi, pour quelle démarche, avec quelle issue. Il ne
 * reçoit ni la valeur lue, ni la réponse par oui ou par non, ni l'identifiant unique : la personne
 * y figure sous le pseudonyme transmis par la passerelle.
 */
@Component
public class JournalConnecteur {

    /** Issue d'une requête menée à son terme ; un refus est tracé sous le nom de son motif. */
    public static final String REUSSI = "REUSSI";

    /** Le certificat de chiffrement présenté n'est pas celui du service demandeur. */
    public static final String CERTIFICAT_REFUSE = "CERTIFICAT_REFUSE";

    private final JdbcClient jdbc;

    public JournalConnecteur(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void tracer(String idEchange, Requete requete, String resultat) {
        jdbc.sql(
                        """
                        INSERT INTO journal (id_echange, demandeur, demarche, finalite, jeu, niveau,
                                             objet, pseudonyme, autorisation, canal, resultat)
                        VALUES (:id_echange, :demandeur, :demarche, :finalite, :jeu, :niveau,
                                :objet, :pseudonyme, :autorisation, :canal, :resultat)
                        """)
                .param("id_echange", idEchange)
                .param("demandeur", requete.demandeur().membre())
                .param("demarche", requete.demarche())
                .param("finalite", requete.finalite())
                .param("jeu", requete.jeu().code())
                .param("niveau", requete.jeu().niveau().name())
                .param("objet", requete.jeu().objet())
                .param("pseudonyme", requete.sujet().pseudonyme())
                .param("autorisation", requete.autorisation().id())
                .param("canal", requete.autorisation().canal())
                .param("resultat", resultat)
                .update();
    }
}
