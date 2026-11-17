package bf.sira.passerelle.demandes;

import bf.sira.consentement.Canal;
import bf.sira.consentement.Citoyens;
import bf.sira.consentement.Codes;
import bf.sira.passerelle.config.Formes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ce qu'un service envoie pour créer une demande (contrat §7.1). Il nomme une démarche, jamais des
 * jeux : ceux-ci viennent de son habilitation.
 *
 * @param retourUrl canal REDIRECTION : où renvoyer le navigateur après la décision
 * @param identifiantUnique le citoyen visé : sur NOTIFICATION (ou le pseudonyme) ; facultatif sur
 *     REDIRECTION et CODE, où seul ce citoyen pourra se rattacher à la demande
 * @param pseudonyme canal NOTIFICATION : le citoyen visé, par le pseudonyme déjà reçu
 * @param certificatChiffrement certificat pour lequel les attestations seront chiffrées
 */
record NouvelleDemande(
        @NotNull @Pattern(regexp = Formes.CODE) String demarche,
        @NotNull Canal canal,
        @Size(max = 500) String retourUrl,
        @Pattern(regexp = Citoyens.FORME_IDENTIFIANT) String identifiantUnique,
        @Pattern(regexp = "[A-Za-z0-9_=+/.-]{1,128}") String pseudonyme,
        @NotBlank @Size(max = 32768) String certificatChiffrement) {

    /** Ce qui distingue deux requêtes portant la même clé d'idempotence. */
    String empreinte() {
        return Codes.empreinte(
                String.join(
                        "\n",
                        demarche,
                        canal.name(),
                        String.valueOf(retourUrl),
                        String.valueOf(identifiantUnique),
                        String.valueOf(pseudonyme),
                        certificatChiffrement));
    }
}
