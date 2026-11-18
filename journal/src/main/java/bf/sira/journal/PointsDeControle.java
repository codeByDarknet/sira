package bf.sira.journal;

import bf.sira.commun.donnees.Sql;
import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Attentes;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.TypesJose;
import bf.sira.commun.jose.VerificateurJws;
import bf.sira.commun.pki.Pki;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Points de contrôle du journal : à intervalle régulier, le cachet de la passerelle signe
 * l'extrémité de la chaîne. Qui réécrirait le journal, chaîne comprise, devrait aussi refaire cette
 * signature (contrat §10).
 */
@Component
public class PointsDeControle {

    static final String LIGNE = "ligne";
    static final String HACHAGE = "hachage";

    private final JdbcClient sql;
    private final Signataire signataire;
    private final VerificateurJws verificateur;
    private final Pki pki;
    private final Clock horloge;

    PointsDeControle(
            JdbcClient sql,
            Signataire signataire,
            VerificateurJws verificateur,
            Pki pki,
            Clock horloge) {
        this.sql = sql;
        this.signataire = signataire;
        this.verificateur = verificateur;
        this.pki = pki;
        this.horloge = horloge;
    }

    /**
     * @param ligne numéro de la dernière ligne couverte
     */
    public record PointDeControle(Instant horodatage, long ligne, String jws) {}

    /** Signe l'extrémité de la chaîne si le journal a avancé depuis le dernier point. */
    @Scheduled(
            initialDelayString = "${sira.passerelle.journal.intervalle-points-de-controle:5m}",
            fixedDelayString = "${sira.passerelle.journal.intervalle-points-de-controle:5m}")
    @Transactional
    public Optional<PointDeControle> produire() {
        return sql.sql(
                        "SELECT numero, hachage FROM journal WHERE numero > COALESCE((SELECT"
                                + " max(ligne) FROM point_de_controle), 0) ORDER BY numero DESC"
                                + " LIMIT 1")
                .query((ligne, i) -> signer(ligne.getLong(1), ligne.getBytes(2)))
                .optional();
    }

    public Optional<PointDeControle> dernier() {
        return sql.sql(
                        "SELECT horodatage, ligne, jws FROM point_de_controle ORDER BY ligne DESC"
                                + " LIMIT 1")
                .query((ligne, i) ->
                        new PointDeControle(
                                Sql.instant(ligne, "horodatage"),
                                ligne.getLong("ligne"),
                                ligne.getString("jws")))
                .optional();
    }

    /**
     * @return le hachage que le point de contrôle certifie pour sa ligne
     * @throws bf.sira.commun.jose.JoseRefuse si la signature n'est pas celle de la passerelle
     */
    byte[] hachageCertifie(PointDeControle point) {
        JWTClaimsSet revendications =
                verificateur.verifier(
                        point.jws(),
                        new Attentes(
                                TypesJose.POINT_DE_CONTROLE,
                                IdentiteMembre.Type.SOCLE,
                                IdentiteMembre.PASSERELLE,
                                IdentiteMembre.PASSERELLE,
                                false,
                                null));
        Object ligne = revendications.getClaim(LIGNE);
        Object hachage = revendications.getClaim(HACHAGE);
        if (!(ligne instanceof Number numero)
                || numero.longValue() != point.ligne()
                || !(hachage instanceof String hexadecimal)) {
            throw new IllegalStateException("point de contrôle incohérent");
        }
        return HexFormat.of().parseHex(hexadecimal);
    }

    private PointDeControle signer(long ligne, byte[] hachage) {
        Instant maintenant = horloge.instant();
        JWTClaimsSet revendications =
                new JWTClaimsSet.Builder()
                        .issuer(signataire.identite().uri())
                        .audience(IdentiteMembre.PASSERELLE.uri())
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(maintenant))
                        // Un point de contrôle vaut aussi longtemps que le cachet qui le signe.
                        .expirationTime(pki.cachet().certificat().getNotAfter())
                        .claim(LIGNE, ligne)
                        .claim(HACHAGE, HexFormat.of().formatHex(hachage))
                        .build();
        PointDeControle point =
                new PointDeControle(
                        maintenant,
                        ligne,
                        signataire.signer(TypesJose.POINT_DE_CONTROLE, revendications));
        sql.sql("INSERT INTO point_de_controle (id, ligne, horodatage, jws) VALUES (?, ?, ?, ?)")
                .params(UUID.randomUUID(), ligne, Sql.date(maintenant), point.jws())
                .update();
        return point;
    }
}
