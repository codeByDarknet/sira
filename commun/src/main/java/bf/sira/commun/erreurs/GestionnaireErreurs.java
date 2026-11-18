package bf.sira.commun.erreurs;

import bf.sira.commun.jose.JoseRefuse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Gestionnaire global des erreurs : toute réponse d'erreur est un {@code application/problem+json}
 * au libellé convenu. Les messages des exceptions (qui peuvent citer une valeur reçue ou une
 * requête SQL) ne sortent jamais, ni dans la réponse ni dans le journal applicatif.
 */
@RestControllerAdvice
public class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    private static final Logger journal = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(ErreurMetier.class)
    ResponseEntity<ProblemDetail> erreurMetier(ErreurMetier erreur) {
        return reponse(Problemes.pour(erreur));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> contrainte(ConstraintViolationException erreur) {
        return reponse(Problemes.pour(HttpStatus.BAD_REQUEST));
    }

    /** Signature, certificat ou chiffrement refusé : le motif reste dans le journal. */
    @ExceptionHandler(JoseRefuse.class)
    ResponseEntity<ProblemDetail> joseRefuse(JoseRefuse refus) {
        journal.warn("Objet signé ou chiffré refusé : motif={}", refus.motif());
        return reponse(Problemes.pour(HttpStatus.FORBIDDEN));
    }

    /** Les refus de sécurité levés dans un contrôleur sont rendus à Spring Security. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void securite(RuntimeException erreur) {
        throw erreur;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inattendue(Exception erreur) {
        String reference = UUID.randomUUID().toString();
        journal.error(
                "Erreur inattendue : reference={} type={}", reference, erreur.getClass().getName());
        journal.debug("Détail de l'erreur {}", reference, erreur);
        ProblemDetail probleme = Problemes.pour(HttpStatus.INTERNAL_SERVER_ERROR);
        probleme.setProperty("reference", reference);
        return reponse(probleme);
    }

    /** Champs refusés par Bean Validation : on nomme le champ et la règle, jamais la valeur. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erreur,
            HttpHeaders enTetes,
            HttpStatusCode statut,
            WebRequest requete) {
        List<Map<String, String>> champs =
                erreur.getBindingResult().getFieldErrors().stream()
                        .map(champ ->
                                Map.of(
                                        "champ", champ.getField(),
                                        "regle", String.valueOf(champ.getCode())))
                        .toList();
        ProblemDetail probleme = Problemes.pour(HttpStatus.BAD_REQUEST);
        probleme.setProperty("erreurs", champs);
        return new ResponseEntity<>(probleme, enTetesProbleme(), HttpStatus.BAD_REQUEST);
    }

    /** Toutes les autres erreurs de Spring MVC : libellé générique du statut, sans détail. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erreur,
            Object corps,
            HttpHeaders enTetes,
            HttpStatusCode statut,
            WebRequest requete) {
        // Corps coupé par LimiteCorps pendant sa lecture : c'est un 413, pas un JSON mal formé.
        if (NestedExceptionUtils.getRootCause(erreur) instanceof CorpsTropVolumineux) {
            statut = HttpStatus.CONTENT_TOO_LARGE;
        }
        HttpHeaders sortie = enTetesProbleme();
        // « Allow » (405) et « Accept » (415) guident l'appelant sans rien révéler.
        if (enTetes != null) {
            sortie.putAll(enTetes);
            sortie.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return new ResponseEntity<>(Problemes.pour(statut), sortie, statut);
    }

    private static ResponseEntity<ProblemDetail> reponse(ProblemDetail probleme) {
        return ResponseEntity.status(probleme.getStatus())
                .headers(enTetesProbleme())
                .body(probleme);
    }

    private static HttpHeaders enTetesProbleme() {
        HttpHeaders enTetes = new HttpHeaders();
        enTetes.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return enTetes;
    }
}
