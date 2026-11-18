import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, request, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS } from '../../outils/comptes';
import { adresseAutorisation, retourDe, suivreRetours } from '../../outils/oidc';

const COMPTES = resolve(__dirname, '../../../../identite/demo/sira-citoyens-users-0.json');

test.describe('Téléphone simulé', () => {
  test('propose tous les citoyens de la démonstration, par leur nom', async ({ page }) => {
    const { users } = JSON.parse(readFileSync(COMPTES, 'utf8')) as {
      users: { firstName: string; lastName: string }[];
    };
    await page.goto(ADRESSES.telephone);
    await expect(page.getByLabel('Téléphone de').getByRole('option')).toHaveText(
      users.map((compte) => `${compte.firstName} ${compte.lastName}`),
    );
  });

  test('affiche le code envoyé, le copie, et ce code ouvre la session', async ({ context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    const citoyen = CITOYENS.rasmata;

    const telephone = await context.newPage();
    await telephone.goto(ADRESSES.telephone);
    await telephone.getByLabel('Téléphone de').selectOption({ label: citoyen.nom });
    await expect(telephone.getByText('Dans la réalité, ce code arrive par SMS.')).toBeVisible();
    await expect(
      telephone.getByRole('heading', { name: `Messages de ${citoyen.nom}` }),
    ).toBeVisible();
    // Le code affiché est lu chiffre à chiffre par les lecteurs d'écran : « 4 9 2 1 1 3 ».
    const affiche = telephone.getByText(/^\d( \d){5}$/);
    const avant = (await affiche.count()) ? await affiche.textContent() : null;

    const connexion = await context.newPage();
    const attendreRetour = suivreRetours(connexion);
    await connexion.goto(
      adresseAutorisation('sira-citoyens', 'espace-citoyen', retourDe(ADRESSES.citoyen)),
    );
    await connexion.getByLabel('Identifiant unique').fill(citoyen.identifiant);
    await connexion.getByRole('button', { name: 'Continuer' }).click();
    await expect(connexion.getByLabel('Code à 6 chiffres reçu par message')).toBeVisible();

    // Le téléphone se met à jour seul : aucun rechargement de la page.
    await telephone.bringToFront();
    await expect(affiche).toBeVisible();
    if (avant) {
      await expect(affiche).not.toHaveText(avant);
    }
    await telephone.getByRole('button', { name: 'Copier le code' }).click();
    await expect(telephone.getByText('Code copié.')).toBeVisible();
    const code = await telephone.evaluate(() => navigator.clipboard.readText());
    expect(code.split('').join(' ')).toBe(await affiche.textContent());

    await connexion.bringToFront();
    await connexion.getByLabel('Code à 6 chiffres reçu par message').fill(code);
    await connexion.getByRole('button', { name: 'Valider' }).click();
    await attendreRetour(ADRESSES.citoyen);
  });

  test('ne donne à la messagerie qu’un accès en lecture, à la seule recherche', async () => {
    const telephone = new URL(ADRESSES.telephone);
    const contexte = await request.newContext({
      baseURL: `http://127.0.0.1:${telephone.port || '80'}`,
      extraHTTPHeaders: { Host: telephone.host },
    });
    const statut = async (methode: string, chemin: string) =>
      (await contexte.fetch(chemin, { method: methode })).status();

    expect(await statut('GET', '/messagerie/search?query=sira')).toBe(200);
    expect(await statut('DELETE', '/messagerie/search')).toBe(403);
    expect(await statut('POST', '/messagerie/search')).toBe(403);
    expect(await statut('GET', '/messagerie/messages')).toBe(404);
    expect(await statut('DELETE', '/messagerie/messages')).toBe(404);
    expect(await statut('GET', '/api/v1/messages')).toBe(404);
    expect(await statut('DELETE', '/api/v1/messages')).toBe(404);
    await contexte.dispose();
  });
});
