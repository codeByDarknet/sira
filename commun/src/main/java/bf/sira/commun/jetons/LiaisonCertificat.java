package bf.sira.commun.jetons;

import bf.sira.commun.crypto.TempsConstant;
import bf.sira.commun.identite.SecuriteMembres;
import bf.sira.commun.pki.Certificats;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Exige qu'un jeton d'accès soit lié (RFC 8705, {@code cnf.x5t#S256}) au certificat client de la
 * connexion qui le présente. Un jeton volé est inutilisable sans la clé privée de son titulaire ;
 * un jeton non lié est refusé.
 */
public final class LiaisonCertificat implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REFUS =
            new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN, "jeton non lié au certificat présenté", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jeton) {
        Optional<String> presente =
                RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                        ? SecuriteMembres.certificatDe(a.getRequest())
                                .map(Certificats::empreinteSha256)
                        : Optional.empty();
        boolean lie =
                jeton.getClaims().get("cnf") instanceof Map<?, ?> cnf
                        && cnf.get("x5t#S256") instanceof String attendue
                        && presente.filter(p -> TempsConstant.egaux(p, attendue)).isPresent();
        return lie
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(REFUS);
    }
}
