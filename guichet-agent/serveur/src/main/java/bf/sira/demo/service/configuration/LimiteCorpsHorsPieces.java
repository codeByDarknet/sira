package bf.sira.demo.service.configuration;

import bf.sira.commun.erreurs.LimiteCorps;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.unit.DataSize;

/**
 * La borne commune des corps de requête ({@code sira.http.taille-corps-max}) est relevée pour
 * qu'une pièce PDF de 2 Mo puisse être jointe. Toutes les autres routes gardent la borne habituelle
 * de 256 Kio : seul le dépôt d'une pièce, en PUT, y échappe.
 */
@Configuration(proxyBeanMethods = false)
class LimiteCorpsHorsPieces {

    static final DataSize BORNE = DataSize.ofKilobytes(256);

    private static final Pattern DEPOT_DE_PIECE =
            Pattern.compile("/api/parcours/dossier/declarations/[a-z0-9.-]{1,80}/piece");

    @Bean
    FilterRegistrationBean<LimiteCorps> filtreLimiteCorpsHorsPieces() {
        FilterRegistrationBean<LimiteCorps> filtre =
                new FilterRegistrationBean<>(new Filtre(BORNE.toBytes()));
        filtre.setName("limiteCorpsHorsPieces");
        filtre.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return filtre;
    }

    private static final class Filtre extends LimiteCorps {

        Filtre(long tailleMax) {
            super(tailleMax);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest requete) {
            return "PUT".equals(requete.getMethod())
                    && DEPOT_DE_PIECE.matcher(requete.getRequestURI()).matches();
        }
    }
}
