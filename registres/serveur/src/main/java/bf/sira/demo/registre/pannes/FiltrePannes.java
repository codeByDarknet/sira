package bf.sira.demo.registre.pannes;

import bf.sira.commun.erreurs.Problemes;
import bf.sira.demo.registre.appels.Appel;
import bf.sira.demo.registre.appels.DepotAppels;
import bf.sira.demo.registre.configuration.ProprietesRegistre;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Devant l'API interne et le contrat commun ({@code /v1/}) : applique la panne choisie par l'agent
 * et consigne chaque appel reçu. Le back-office, lui, n'est jamais mis en panne : l'agent doit
 * pouvoir rétablir le registre.
 */
@Component
class FiltrePannes extends OncePerRequestFilter {

    private final DepotPanne pannes;
    private final DepotAppels appels;
    private final Duration dureeSilence;

    FiltrePannes(DepotPanne pannes, DepotAppels appels, ProprietesRegistre registre) {
        this.pannes = pannes;
        this.appels = appels;
        this.dureeSilence = registre.dureeSilence();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String uri = requete.getRequestURI();
        // La sonde du contrat commun ne lit aucune donnée : elle n'est ni mise en panne ni
        // consignée parmi les appels.
        return !(uri.startsWith("/interne/") || uri.startsWith("/v1/")) || uri.equals("/v1/sante");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        long debut = System.nanoTime();
        Panne panne = pannes.lire();
        Appel.Type type =
                requete.getRequestURI().contains("/documents/")
                        ? Appel.Type.DOCUMENT
                        : Appel.Type.FICHE;
        String origine = requete.getRemoteAddr();
        switch (panne.mode()) {
            case NORMAL -> chaine.doFilter(requete, reponse);
            case LENT -> {
                attendre(Duration.ofMillis(panne.delaiMs()));
                chaine.doFilter(requete, reponse);
            }
            case ERREURS -> Problemes.ecrire(reponse, HttpStatus.INTERNAL_SERVER_ERROR);
            case MUET -> {
                // Consigné avant le silence : l'agent voit l'appel arriver, pas trente secondes
                // après.
                appels.consigner(type, panne.mode(), null, 0, origine);
                attendre(dureeSilence);
                Problemes.ecrire(reponse, HttpStatus.SERVICE_UNAVAILABLE);
                return;
            }
        }
        appels.consigner(
                type,
                panne.mode(),
                reponse.getStatus(),
                (int) Duration.ofNanos(System.nanoTime() - debut).toMillis(),
                origine);
    }

    private static void attendre(Duration duree) {
        try {
            Thread.sleep(duree);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
