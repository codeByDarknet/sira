package bf.sira.resilience;

import bf.sira.commun.donnees.Sql;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * La protection de chaque registre (contrat §6) : un disjoncteur pour cesser d'appeler un registre
 * en panne, une cloison pour qu'un registre lent n'accapare pas la passerelle, et de nouveaux
 * essais pour l'appel immédiat. Seule une panne passagère ({@link ConnecteurInjoignable}) compte
 * comme un échec : un refus du registre est une réponse.
 */
@Component
public class Disjoncteurs {

    /** L'état d'un disjoncteur, tel que le lit la console. */
    public enum Etat {
        FERME,
        OUVERT,
        SEMI_OUVERT
    }

    private static final Logger JOURNAL = LoggerFactory.getLogger(Disjoncteurs.class);

    private final CircuitBreakerConfig reglageDisjoncteur;
    private final BulkheadConfig reglageCloison;
    private final RetryConfig reglageEssais;
    private final Map<String, CircuitBreaker> disjoncteurs = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> cloisons = new ConcurrentHashMap<>();
    private final Map<String, Retry> essais = new ConcurrentHashMap<>();
    private final JdbcClient sql;
    private final Clock horloge;
    private final int appelsSemiOuvert;

    Disjoncteurs(ReglageDisjoncteur reglage, ReglageEssais essais, JdbcClient sql, Clock horloge) {
        this.reglageDisjoncteur =
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(reglage.fenetre())
                        .minimumNumberOfCalls(reglage.appelsMinimum())
                        .failureRateThreshold(reglage.seuilEchecs())
                        .waitDurationInOpenState(reglage.attenteOuvert())
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .permittedNumberOfCallsInHalfOpenState(reglage.appelsSemiOuvert())
                        .recordExceptions(ConnecteurInjoignable.class)
                        .build();
        this.reglageCloison =
                BulkheadConfig.custom()
                        .maxConcurrentCalls(essais.appelsSimultanes())
                        .maxWaitDuration(Duration.ZERO)
                        .build();
        this.reglageEssais =
                RetryConfig.custom()
                        .maxAttempts(essais.tentatives())
                        .waitDuration(essais.attenteEntreTentatives())
                        .retryExceptions(ConnecteurInjoignable.class)
                        .build();
        this.sql = sql;
        this.horloge = horloge;
        this.appelsSemiOuvert = reglage.appelsSemiOuvert();
    }

    /**
     * Appelle un registre sous sa protection.
     *
     * @param immediat l'appelant attend : un second essai est tenté sans délai notable
     * @throws ConnecteurInjoignable si le registre est en panne
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException si le disjoncteur est
     *     ouvert
     * @throws io.github.resilience4j.bulkhead.BulkheadFullException si trop d'appels sont en cours
     */
    public <T> T appeler(String registre, boolean immediat, Supplier<T> appel) {
        Supplier<T> protege =
                Bulkhead.decorateSupplier(
                        cloisons.computeIfAbsent(registre, nom -> Bulkhead.of(nom, reglageCloison)),
                        CircuitBreaker.decorateSupplier(disjoncteur(registre), appel));
        if (!immediat) {
            return protege.get();
        }
        Retry essai = essais.computeIfAbsent(registre, nom -> Retry.of(nom, reglageEssais));
        return Retry.decorateSupplier(essai, protege).get();
    }

    public Etat etat(String registre) {
        return switch (disjoncteur(registre).getState()) {
            case OPEN, FORCED_OPEN -> Etat.OUVERT;
            case HALF_OPEN -> Etat.SEMI_OUVERT;
            default -> Etat.FERME;
        };
    }

    /** Les registres qu'il est inutile d'appeler pour l'instant. */
    public List<String> ouverts() {
        return disjoncteurs.keySet().stream()
                .filter(registre -> etat(registre) == Etat.OUVERT)
                .toList();
    }

    private CircuitBreaker disjoncteur(String registre) {
        return disjoncteurs.computeIfAbsent(registre, this::creer);
    }

    private CircuitBreaker creer(String registre) {
        CircuitBreaker disjoncteur = CircuitBreaker.of(registre, reglageDisjoncteur);
        disjoncteur
                .getEventPublisher()
                .onStateTransition(transition ->
                        reprendre(registre, transition.getStateTransition().getToState()));
        return disjoncteur;
    }

    /**
     * La file n'attend pas la fin de ses délais quand un registre revient : quelques éléments
     * servent d'essai dès que le disjoncteur s'entrouvre, tous repartent dès qu'il se referme.
     */
    private void reprendre(String registre, CircuitBreaker.State etat) {
        if (etat != CircuitBreaker.State.HALF_OPEN && etat != CircuitBreaker.State.CLOSED) {
            return;
        }
        int limite = etat == CircuitBreaker.State.CLOSED ? Integer.MAX_VALUE : appelsSemiOuvert;
        try {
            sql.sql(
                            "UPDATE element SET prochaine_tentative_le = ? WHERE id IN (SELECT id"
                                    + " FROM element WHERE etat = 'A_TRAITER' AND registre = ?"
                                    + " ORDER BY cree_le LIMIT ?)")
                    .params(Sql.date(horloge.instant()), registre, limite)
                    .update();
        } catch (DataAccessException e) {
            // Sans cette avance, les éléments repartent quand même, à l'échéance de leur délai.
            JOURNAL.warn("Reprise du registre {} non avancée : {}", registre, e.getMessage());
        }
    }
}
