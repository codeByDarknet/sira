import { createHash, randomBytes } from 'node:crypto';
import { expect, type Page } from '@playwright/test';
import { ADRESSES, sur } from './adresses';

/**
 * Adresse d'autorisation OIDC d'un client, telle que son serveur d'application la construirait.
 * Sert à éprouver l'écran de connexion d'un royaume sans passer par l'application : le retour
 * porte un code que seul le serveur, authentifié par son certificat, pourrait échanger.
 */
/** Seule adresse de retour admise par les royaumes pour une application. */
export function retourDe(application: string): string {
  return `${application}/api/auth/retour`;
}

/**
 * Suit les retours d'une page vers les applications. La connexion a réussi quand le navigateur
 * repart vers l'application avec un code d'autorisation ; le serveur de l'application, qui n'a pas
 * lancé cette connexion, la refuse ensuite et renvoie vers sa page d'accueil.
 */
export function suivreRetours(page: Page): (application: string) => Promise<void> {
  const retours: URL[] = [];
  page.on('request', (requete) => {
    const url = new URL(requete.url());
    if (url.pathname === '/api/auth/retour') {
      retours.push(url);
    }
  });
  return async (application) => {
    await expect
      .poll(() => retours.some((url) => sur(application)(url) && url.searchParams.has('code')))
      .toBe(true);
  };
}

export function adresseAutorisation(royaume: string, client: string, retour: string): string {
  const defi = createHash('sha256')
    .update(randomBytes(32).toString('base64url'))
    .digest('base64url');
  const parametres = new URLSearchParams({
    client_id: client,
    redirect_uri: retour,
    response_type: 'code',
    scope: 'openid',
    state: randomBytes(8).toString('hex'),
    code_challenge: defi,
    code_challenge_method: 'S256',
  });
  return `${ADRESSES.identite}/realms/${royaume}/protocol/openid-connect/auth?${parametres}`;
}
