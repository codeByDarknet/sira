package bf.sira.console;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.pki.Certificats;
import bf.sira.commun.test.PkiDeTest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLParameters;

/**
 * Passerelle simulée : exige le mTLS comme la vraie, retient ce qu'elle reçoit et répond ce que le
 * test a prévu.
 */
public final class FaussePasserelle implements AutoCloseable {

    /** Une requête reçue, telle que le relais l'a envoyée. */
    public record Recue(
            String methode,
            String chemin,
            String parametres,
            Headers enTetes,
            String corps,
            String membre) {}

    private record Reponse(int statut, String type, String corps) {}

    private final HttpsServer serveur;
    private final List<Recue> recues = new CopyOnWriteArrayList<>();
    private volatile Reponse reponse;

    public FaussePasserelle(PkiDeTest pki) {
        try {
            serveur =
                    HttpsServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.setHttpsConfigurator(
                new HttpsConfigurator(pki.contexteTls(pki.tls(IdentiteMembre.PASSERELLE))) {
                    @Override
                    public void configure(HttpsParameters parametres) {
                        SSLParameters tls = getSSLContext().getDefaultSSLParameters();
                        tls.setNeedClientAuth(true);
                        parametres.setSSLParameters(tls);
                    }
                });
        serveur.createContext("/", this::traiter);
        serveur.start();
        reinitialiser();
    }

    public String url() {
        return "https://localhost:" + serveur.getAddress().getPort();
    }

    /** Oublie les requêtes reçues et revient à la réponse par défaut ({@code 200 {}}). */
    public void reinitialiser() {
        recues.clear();
        repondra(200, "application/json", "{}");
    }

    public void repondra(int statut, String type, String corps) {
        reponse = new Reponse(statut, type, corps);
    }

    public List<Recue> recues() {
        return recues;
    }

    /** La seule requête reçue ; échoue s'il y en a eu zéro ou plusieurs. */
    public Recue seuleRecue() {
        if (recues.size() != 1) {
            throw new AssertionError("requêtes reçues par la passerelle : " + recues.size());
        }
        return recues.getFirst();
    }

    private void traiter(HttpExchange echange) throws IOException {
        X509Certificate client =
                (X509Certificate)
                        ((HttpsExchange) echange).getSSLSession().getPeerCertificates()[0];
        recues.add(
                new Recue(
                        echange.getRequestMethod(),
                        echange.getRequestURI().getRawPath(),
                        echange.getRequestURI().getRawQuery(),
                        echange.getRequestHeaders(),
                        new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        Certificats.uriSan(client).orElse("")));
        Reponse prevue = reponse;
        byte[] corps = prevue.corps().getBytes(StandardCharsets.UTF_8);
        if (prevue.type() != null) {
            echange.getResponseHeaders().add("Content-Type", prevue.type());
        }
        echange.getResponseHeaders().add("Set-Cookie", "passerelle=interne");
        echange.sendResponseHeaders(prevue.statut(), corps.length == 0 ? -1 : corps.length);
        try (var sortie = echange.getResponseBody()) {
            sortie.write(corps);
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
