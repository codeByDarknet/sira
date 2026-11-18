package bf.sira.commun.erreurs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Borne la taille des corps de requête. Une taille annoncée trop grande est refusée d'emblée (413)
 * ; un corps sans taille annoncée est coupé dès qu'il dépasse la borne.
 */
public class LimiteCorps extends OncePerRequestFilter {

    private final long tailleMax;

    public LimiteCorps(long tailleMax) {
        this.tailleMax = tailleMax;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        if (requete.getContentLengthLong() > tailleMax) {
            Problemes.ecrire(reponse, HttpStatus.CONTENT_TOO_LARGE);
            return;
        }
        try {
            chaine.doFilter(new RequeteBornee(requete), reponse);
        } catch (CorpsTropVolumineux e) {
            if (!reponse.isCommitted()) {
                reponse.reset();
                Problemes.ecrire(reponse, HttpStatus.CONTENT_TOO_LARGE);
            }
        }
    }

    private final class RequeteBornee extends HttpServletRequestWrapper {

        // Compté pour toute la requête : relire le flux ne remet pas le compteur à zéro.
        private long lus;

        RequeteBornee(HttpServletRequest requete) {
            super(requete);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encodage = getCharacterEncoding();
            Charset jeu = encodage != null ? Charset.forName(encodage) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), jeu));
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream flux = super.getInputStream();
            return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    int octet = flux.read();
                    if (octet != -1) {
                        compter(1);
                    }
                    return octet;
                }

                @Override
                public int read(byte[] tampon, int debut, int longueur) throws IOException {
                    int nombre = flux.read(tampon, debut, longueur);
                    if (nombre > 0) {
                        compter(nombre);
                    }
                    return nombre;
                }

                private void compter(int nombre) throws CorpsTropVolumineux {
                    lus += nombre;
                    if (lus > tailleMax) {
                        throw new CorpsTropVolumineux();
                    }
                }

                @Override
                public boolean isFinished() {
                    return flux.isFinished();
                }

                @Override
                public boolean isReady() {
                    return flux.isReady();
                }

                @Override
                public void setReadListener(ReadListener ecouteur) {
                    flux.setReadListener(ecouteur);
                }
            };
        }
    }
}
