package bf.sira.espacecitoyen.relais;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Liste blanche des routes du citoyen (docs/architecture/API.md). Ce qui n'y figure pas n'atteint
 * jamais la passerelle.
 */
final class RoutesCitoyen {

    private static final List<Route> ROUTES =
            List.of(
                    Route.de(GET, "/resume"),
                    Route.de(GET, "/demandes"),
                    Route.de(POST, "/demandes/rattachement"),
                    Route.de(GET, "/demandes/{id}"),
                    Route.de(POST, "/demandes/{id}/rattachement"),
                    Route.de(POST, "/demandes/{id}/decision"),
                    Route.de(GET, "/autorisations"),
                    Route.de(GET, "/autorisations/{id}"),
                    Route.de(DELETE, "/autorisations/{id}"),
                    Route.de(GET, "/journal"),
                    Route.de(POST, "/journal/{id}/signalement"));

    private RoutesCitoyen() {}

    static boolean autorise(HttpMethod methode, String chemin) {
        return ROUTES.stream().anyMatch(route -> route.accepte(methode, chemin));
    }
}
