import { Page } from '@playwright/test';

export interface Reponse<T> {
  statut: number;
  corps: T;
  texte: string;
}

/**
 * Appelle une route `/api/**` de l'écran ouvert, depuis la page elle-même : mêmes cookies, même
 * origine et mêmes protections que l'application. Une écriture porte le jeton `XSRF-TOKEN` ;
 * `corps`, s'il est donné, part en JSON.
 */
export async function appeler<T = unknown>(
  page: Page,
  chemin: string,
  methode: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
  corps?: unknown,
): Promise<Reponse<T>> {
  const { statut, texte } = await page.evaluate(
    async ([adresse, verbe, json]) => {
      const jeton = /(?:^|; )XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1];
      const reponse = await fetch(adresse, {
        method: verbe,
        headers: {
          Accept: 'application/json',
          ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(verbe !== 'GET' && jeton ? { 'X-XSRF-TOKEN': decodeURIComponent(jeton) } : {}),
        },
        body: json,
      });
      return { statut: reponse.status, texte: await reponse.text() };
    },
    [chemin, methode, corps === undefined ? undefined : JSON.stringify(corps)] as const,
  );
  let lu: unknown = null;
  try {
    lu = JSON.parse(texte);
  } catch {
    // Une réponse sans JSON (204, page d'erreur) se juge à son statut.
  }
  return { statut, corps: lu as T, texte };
}

/** Retient le texte de toutes les réponses `/api/**` que cette page reçoit de cet écran. */
export function ecouterReponses(page: Page, adresse: string): () => Promise<string[]> {
  const recues: Promise<string>[] = [];
  page.on('response', (reponse) => {
    if (reponse.url().startsWith(`${adresse}/api/`)) {
      recues.push(reponse.text().catch(() => ''));
    }
  });
  return () => Promise.all(recues);
}
