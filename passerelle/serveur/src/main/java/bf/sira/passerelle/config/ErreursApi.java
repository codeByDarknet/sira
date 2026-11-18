package bf.sira.passerelle.config;

import bf.sira.commun.erreurs.ErreurMetier;
import bf.sira.commun.erreurs.Problemes;
import bf.sira.commun.erreurs.SaisieRefusee;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Complète les problèmes RFC 9457 du socle commun avec ce que décrit l'API de la passerelle : un
 * {@code code} métier stable et, pour une saisie refusée, la liste {@code erreurs} par champ.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ErreursApi {

    private static final String CODE_VALIDATION = "VALIDATION";

    private static final Map<String, String> MESSAGES =
            Map.of(
                    "NotBlank", "Ce champ est obligatoire.",
                    "NotNull", "Ce champ est obligatoire.",
                    "NotEmpty", "Ce champ est obligatoire.",
                    "Size", "La longueur de ce champ n'est pas admise.",
                    "Pattern", "Le format de ce champ n'est pas admis.",
                    "Min", "Cette valeur est trop petite.",
                    "Max", "Cette valeur est trop grande.");

    @ExceptionHandler(ErreurMetier.class)
    ResponseEntity<ProblemDetail> erreurMetier(ErreurMetier erreur) {
        ProblemDetail probleme = Problemes.pour(erreur);
        probleme.setProperty("code", erreur.code().toUpperCase(Locale.ROOT).replace('-', '_'));
        return reponse(probleme);
    }

    @ExceptionHandler(SaisieRefusee.class)
    ResponseEntity<ProblemDetail> saisieRefusee(SaisieRefusee refus) {
        return validation(List.of(champ(refus.champ(), refus.getMessage())));
    }

    /** On nomme le champ et la règle enfreinte, jamais la valeur reçue. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> champsRefuses(MethodArgumentNotValidException erreur) {
        return validation(
                erreur.getBindingResult().getFieldErrors().stream()
                        .map(e -> champ(enSnakeCase(e.getField()), message(e.getCode())))
                        .toList());
    }

    /** Même refus quand la route contraint aussi un en-tête ou un paramètre. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> parametresRefuses(HandlerMethodValidationException erreur) {
        List<Map<String, String>> erreurs = new ArrayList<>();
        for (ParameterValidationResult resultat : erreur.getParameterValidationResults()) {
            if (resultat instanceof ParameterErrors champs) {
                champs.getFieldErrors()
                        .forEach(e ->
                                erreurs.add(
                                        champ(enSnakeCase(e.getField()), message(e.getCode()))));
            } else {
                String nom = enSnakeCase(resultat.getMethodParameter().getParameterName());
                resultat.getResolvableErrors()
                        .forEach(e -> {
                            String[] codes = e.getCodes();
                            erreurs.add(
                                    champ(
                                            nom,
                                            message(
                                                    codes == null
                                                            ? null
                                                            : codes[codes.length - 1])));
                        });
            }
        }
        return validation(erreurs);
    }

    private static String message(String regle) {
        return MESSAGES.getOrDefault(String.valueOf(regle), "Cette valeur n'est pas admise.");
    }

    private static ResponseEntity<ProblemDetail> validation(List<Map<String, String>> erreurs) {
        ProblemDetail probleme = Problemes.pour(HttpStatus.BAD_REQUEST);
        probleme.setDetail("Certaines informations saisies ne sont pas admises.");
        probleme.setProperty("code", CODE_VALIDATION);
        probleme.setProperty("erreurs", erreurs);
        return reponse(probleme);
    }

    private static Map<String, String> champ(String nom, String message) {
        return Map.of("champ", nom, "message", message);
    }

    private static String enSnakeCase(String champ) {
        return champ.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static ResponseEntity<ProblemDetail> reponse(ProblemDetail probleme) {
        return ResponseEntity.status(probleme.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(probleme);
    }
}
