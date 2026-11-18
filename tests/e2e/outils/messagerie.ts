import { APIRequestContext, expect, request } from '@playwright/test';
import { ADRESSES } from './adresses';

/** Un message de la messagerie de démonstration, tel que le téléphone simulé le lit. */
interface Message {
  ID: string;
  Snippet: string;
}

const CODE = /\b\d{6}\b/;

export function adresseDe(identifiant: string): string {
  return `${identifiant}@telephone.sira.example`;
}

/**
 * Lit la messagerie par le même relais que le téléphone simulé. L'appel vise la boucle locale et
 * porte le nom d'hôte en en-tête : Node, contrairement aux navigateurs, ne résout pas partout
 * les noms en « .localhost ».
 */
async function ouvrir(): Promise<APIRequestContext> {
  const telephone = new URL(ADRESSES.telephone);
  return request.newContext({
    baseURL: `http://127.0.0.1:${telephone.port || '80'}`,
    extraHTTPHeaders: { Host: telephone.host },
  });
}

async function dernierMessage(contexte: APIRequestContext, identifiant: string) {
  const reponse = await contexte.get('/messagerie/search', {
    params: { query: `to:"${adresseDe(identifiant)}"`, limit: 1 },
  });
  expect(reponse.ok(), `lecture de la messagerie (${reponse.status()})`).toBe(true);
  const { messages } = (await reponse.json()) as { messages: Message[] };
  return messages[0];
}

/**
 * Déclenche un envoi, puis rend le code du message arrivé depuis. Le message précédent est
 * relevé avant l'envoi : un code plus ancien n'est jamais pris pour le nouveau.
 */
export async function attendreCode(
  identifiant: string,
  declencher: () => Promise<void>,
): Promise<string> {
  const contexte = await ouvrir();
  try {
    const avant = (await dernierMessage(contexte, identifiant))?.ID;
    await declencher();
    let code: string | undefined;
    await expect(async () => {
      const message = await dernierMessage(contexte, identifiant);
      expect(message?.ID, 'un nouveau message est arrivé').not.toBe(avant);
      code = CODE.exec(message?.Snippet ?? '')?.[0];
      expect(code, 'le message porte un code à 6 chiffres').toBeDefined();
    }).toPass({ timeout: 15_000 });
    return code as string;
  } finally {
    await contexte.dispose();
  }
}
