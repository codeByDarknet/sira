package bf.sira.commun.oidc;

import org.springframework.web.client.RestClient;

/**
 * Relais vers la passerelle au nom de l'utilisateur connecté : chaque appel part en mTLS et porte
 * son jeton d'accès, renouvelé au besoin. À utiliser dans le fil d'une requête de l'utilisateur.
 *
 * @param client client HTTP dont l'adresse de base est celle de la passerelle
 */
public record RelaisPasserelle(RestClient client) {}
