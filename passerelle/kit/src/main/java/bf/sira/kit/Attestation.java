package bf.sira.kit;

import bf.sira.commun.identite.IdentiteMembre;
import java.time.Instant;

/**
 * Une attestation déchiffrée, dont la signature du registre a été vérifiée.
 *
 * @param id identifiant d'échange, à conserver comme preuve
 * @param registre le registre qui atteste
 * @param pseudonyme le citoyen concerné, sous son pseudonyme chez ce service
 * @param jeu code du jeu de données
 */
public record Attestation(
        String id,
        IdentiteMembre registre,
        String pseudonyme,
        String demarche,
        String jeu,
        Niveau niveau,
        Instant emiseLe,
        Instant expireLe,
        Resultat resultat) {}
