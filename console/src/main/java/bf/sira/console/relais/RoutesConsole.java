package bf.sira.console.relais;

import static bf.sira.console.relais.Acces.DECISION;
import static bf.sira.console.relais.Acces.ECRITURE;
import static bf.sira.console.relais.Acces.LECTURE;
import static bf.sira.console.relais.Acces.TRAITEMENT;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;

/**
 * Liste blanche des routes d'administration (docs/architecture/API.md), chacune avec l'accès
 * qu'elle exige. Ce qui n'y figure pas n'atteint jamais la passerelle.
 */
final class RoutesConsole {

    private static final List<Route> ROUTES = routes();

    private RoutesConsole() {}

    private static List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        routes.add(Route.de(POST, "/jeux/essai", ECRITURE));
        routes.add(Route.de(POST, "/habilitations/{id}/soumission", ECRITURE));
        for (String decision : List.of("approbation", "rejet", "suspension")) {
            routes.add(Route.de(POST, "/habilitations/{id}/" + decision, DECISION));
        }
        routes.add(Route.de(PUT, "/membres/{code}/retours", DECISION));
        routes.add(Route.de(PUT, "/registres/{code}/adresse", DECISION));
        // Ce que le registre expose, lu chez son connecteur : base de l'assistant des questions.
        routes.add(Route.de(GET, "/registres/{code}/schema", LECTURE));
        ressource(routes, "/membres", "{code}");
        ressource(routes, "/registres", "{code}");
        ressource(routes, "/jeux", "{code}");
        ressource(routes, "/demarches", "{code}");
        ressource(routes, "/habilitations", "{id}");
        routes.add(Route.de(GET, "/journal", LECTURE));
        routes.add(Route.de(GET, "/journal/verification", LECTURE));
        routes.add(Route.de(GET, "/signalements", LECTURE));
        routes.add(Route.de(POST, "/signalements/{id}/traitement", TRAITEMENT));
        routes.add(Route.de(GET, "/etat", LECTURE));
        routes.add(Route.de(GET, "/statistiques", LECTURE));
        return List.copyOf(routes);
    }

    /** Liste, lecture, création, modification, désactivation : les cinq routes d'une ressource. */
    private static void ressource(List<Route> routes, String chemin, String cle) {
        String objet = chemin + "/" + cle;
        routes.add(Route.de(GET, chemin, LECTURE));
        routes.add(Route.de(GET, objet, LECTURE));
        routes.add(Route.de(POST, chemin, ECRITURE));
        routes.add(Route.de(PUT, objet, ECRITURE));
        routes.add(Route.de(DELETE, objet, ECRITURE));
    }

    static Optional<Route> trouver(HttpMethod methode, String chemin) {
        return ROUTES.stream().filter(route -> route.accepte(methode, chemin)).findFirst();
    }
}
