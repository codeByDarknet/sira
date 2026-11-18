package bf.sira.demo.service.configuration;

import bf.sira.commun.erreurs.GestionnaireErreurs;
import bf.sira.commun.erreurs.Problemes;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Complète le gestionnaire commun : une saisie refusée nomme chaque champ avec un message que
 * l'écran affiche tel quel sous le champ (forme {@code erreurs: [{champ, message}]} des API). Les
 * messages sont des constantes du code : ils ne reprennent jamais la valeur saisie.
 */
@RestControllerAdvice
class Erreurs extends GestionnaireErreurs {

    /** Refus du module commun (agents, rôles, pièces) : un champ, un message. */
    @ExceptionHandler(bf.sira.commun.erreurs.SaisieRefusee.class)
    ResponseEntity<Object> saisieRefuseeCommune(bf.sira.commun.erreurs.SaisieRefusee refus) {
        return reponse(List.of(Map.of("champ", refus.champ(), "message", refus.getMessage())));
    }

    @ExceptionHandler(SaisieRefusee.class)
    ResponseEntity<Object> saisieRefusee(SaisieRefusee refus) {
        return reponse(List.of(Map.of("champ", refus.champ(), "message", refus.message())));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erreur,
            HttpHeaders enTetes,
            HttpStatusCode statut,
            WebRequest requete) {
        return reponse(
                erreur.getBindingResult().getFieldErrors().stream()
                        .map(champ ->
                                Map.of(
                                        "champ", snakeCase(champ.getField()),
                                        "message", String.valueOf(champ.getDefaultMessage())))
                        .toList());
    }

    private static ResponseEntity<Object> reponse(List<Map<String, String>> erreurs) {
        ProblemDetail probleme = Problemes.pour(HttpStatus.BAD_REQUEST);
        probleme.setProperty("code", "VALIDATION");
        probleme.setProperty("erreurs", erreurs);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(probleme);
    }

    private static String snakeCase(String nom) {
        return nom.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
