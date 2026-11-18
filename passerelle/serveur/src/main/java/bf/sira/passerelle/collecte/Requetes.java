package bf.sira.passerelle.collecte;

import bf.sira.commun.identite.IdentiteMembre;
import bf.sira.commun.jose.Attentes;
import bf.sira.commun.jose.Signataire;
import bf.sira.commun.jose.TypesJose;
import bf.sira.passerelle.referentiel.Jeu;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Fabrique les requêtes signées que la passerelle envoie aux connecteurs (contrat §8). */
@Component
class Requetes {

    private final Signataire signataire;
    private final JsonMapper json;
    private final Clock horloge;

    Requetes(Signataire signataire, JsonMapper json, Clock horloge) {
        this.signataire = signataire;
        this.json = json;
        this.horloge = horloge;
    }

    /**
     * @param identifiantUnique seul endroit où il quitte la passerelle (contrat §5.9)
     * @param pseudonyme le citoyen tel que le demandeur le connaît
     * @param demandeur le membre pour lequel l'attestation sera chiffrée
     * @param certificat son certificat de chiffrement, en PEM
     */
    record Demande(
            String idEchange,
            String registre,
            String identifiantUnique,
            String pseudonyme,
            IdentiteMembre demandeur,
            String certificat,
            String demarche,
            String finalite,
            String autorisation,
            Instant accordeeLe,
            String canal,
            Jeu jeu) {}

    /** Signe une requête valable 60 secondes, pour un seul usage, pour un seul registre. */
    String signer(Demande demande) {
        Instant maintenant = horloge.instant();
        Map<String, Object> autorisation = new LinkedHashMap<>();
        autorisation.put("id", demande.autorisation());
        if (demande.accordeeLe() != null) {
            autorisation.put("accordee_le", demande.accordeeLe().toString());
            autorisation.put("canal", demande.canal());
        }
        JWTClaimsSet revendications =
                new JWTClaimsSet.Builder()
                        .issuer(signataire.identite().uri())
                        .audience(IdentiteMembre.registre(demande.registre()).uri())
                        .jwtID(demande.idEchange())
                        .issueTime(Date.from(maintenant))
                        .expirationTime(Date.from(maintenant.plus(Attentes.VALIDITE_REQUETE)))
                        .claim(
                                "sujet",
                                Map.of(
                                        "identifiant_unique",
                                        demande.identifiantUnique(),
                                        "pseudonyme",
                                        demande.pseudonyme()))
                        .claim(
                                "demandeur",
                                Map.of(
                                        "membre",
                                        demande.demandeur().uri(),
                                        "certificat_chiffrement",
                                        demande.certificat()))
                        .claim("demarche", demande.demarche())
                        .claim("finalite", demande.finalite())
                        .claim("autorisation", autorisation)
                        .claim("jeu", jeu(demande.jeu()))
                        .build();
        return signataire.signer(TypesJose.REQUETE, revendications);
    }

    private Map<String, Object> jeu(Jeu jeu) {
        Map<String, Object> revendication = new LinkedHashMap<>();
        revendication.put("code", jeu.code());
        revendication.put("niveau", jeu.niveau().name());
        revendication.put("validite", jeu.validite());
        switch (jeu.niveau()) {
            case STATUT -> revendication.put("regle", regle(jeu.regle()));
            case CHAMPS -> revendication.put("champs", jeu.champs());
            case DOCUMENT -> revendication.put("document", jeu.document());
        }
        return revendication;
    }

    private Map<String, Object> regle(Jeu.Regle regle) {
        Map<String, Object> revendication = new LinkedHashMap<>();
        revendication.put("champ", regle.champ());
        revendication.put("operateur", regle.operateur().name());
        if (regle.valeur() != null && !regle.valeur().isNull()) {
            revendication.put("valeur", json.treeToValue(regle.valeur(), Object.class));
        }
        return revendication;
    }
}
