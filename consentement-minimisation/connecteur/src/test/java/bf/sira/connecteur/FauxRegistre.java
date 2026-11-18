package bf.sira.connecteur;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Le « système ancien » d'une administration (contrat §9), réduit à l'essentiel : il rend la fiche
 * entière à qui la demande, en HTTP simple.
 */
public final class FauxRegistre implements AutoCloseable {

    /** Ce que le registre répond pour un chemin donné. */
    public record Reponse(int statut, String type, byte[] corps, long attenteMs) {

        public static Reponse json(String corps) {
            return new Reponse(200, "application/json", corps.getBytes(StandardCharsets.UTF_8), 0);
        }

        public static Reponse pdf(byte[] corps) {
            return new Reponse(200, "application/pdf", corps, 0);
        }

        public static Reponse erreur(int statut) {
            return new Reponse(statut, "text/plain", new byte[0], 0);
        }

        public Reponse apres(long attenteMs) {
            return new Reponse(statut, type, corps, attenteMs);
        }
    }

    private static final String RACINE = "/interne/personnes/";

    private final HttpServer serveur;
    private final Map<String, Reponse> reponses = new ConcurrentHashMap<>();
    private final AtomicInteger appels = new AtomicInteger();

    public FauxRegistre() {
        try {
            serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.createContext(RACINE, this::repondre);
        serveur.start();
    }

    public String url() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    /**
     * @param chemin ce qui suit {@code /interne/personnes/} : un identifiant, ou {@code
     *     <identifiant>/documents/<type>}
     */
    public FauxRegistre repond(String chemin, Reponse reponse) {
        reponses.put(chemin, reponse);
        return this;
    }

    public int appels() {
        return appels.get();
    }

    private void repondre(HttpExchange echange) throws IOException {
        appels.incrementAndGet();
        String chemin = echange.getRequestURI().getPath().substring(RACINE.length());
        Reponse reponse = reponses.getOrDefault(chemin, Reponse.erreur(404));
        try (echange) {
            Thread.sleep(reponse.attenteMs());
            echange.getResponseHeaders().add("Content-Type", reponse.type());
            echange.sendResponseHeaders(
                    reponse.statut(), reponse.corps().length == 0 ? -1 : reponse.corps().length);
            echange.getResponseBody().write(reponse.corps());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
