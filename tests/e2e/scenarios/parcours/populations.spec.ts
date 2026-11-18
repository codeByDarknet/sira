import { Page, expect, test } from '@playwright/test';
import { ADRESSES, sur } from '../../outils/adresses';
import { CITOYENS } from '../../outils/comptes';
import { connecterAgentRegistre, connecterCitoyen } from '../../outils/connexion';

/** État de la session d'un écran pour ce navigateur (mêmes cookies), sans passer par son interface. */
async function session(page: Page, ecran: string): Promise<number> {
  return (
    await page.request.get(`${ecran}/api/session`, { headers: { Accept: 'application/json' } })
  ).status();
}

test.describe('Séparation des populations', () => {
  test('un citoyen connecté ne peut pas ouvrir la console', async ({ page }) => {
    await connecterCitoyen(page, CITOYENS.aicha.identifiant);

    expect(await session(page, ADRESSES.console)).toBe(401);
    await page.goto(`${ADRESSES.console}/api/auth/connexion/sira?retour=/`);
    // Sa session de citoyen ne vaut rien ici : la console demande un compte du socle.
    await page.waitForURL(sur(ADRESSES.identite));
    await expect(page.getByLabel('Mot de passe', { exact: true })).toBeVisible();
    await page.getByLabel("Nom d'utilisateur").fill(CITOYENS.aicha.identifiant);
    await page.getByLabel('Mot de passe', { exact: true }).fill('sans-importance');
    await page.getByRole('button', { name: 'Continuer' }).click();
    await expect(page).toHaveURL(sur(ADRESSES.identite));
    expect(await session(page, ADRESSES.console)).toBe(401);
  });

  test('un agent connecté ne peut pas ouvrir l’espace citoyen', async ({ page }) => {
    await connecterAgentRegistre(page, 'casier');

    expect(await session(page, ADRESSES.citoyen)).toBe(401);
    await page.goto(`${ADRESSES.citoyen}/api/auth/connexion/sira?retour=/`);
    await page.waitForURL(sur(ADRESSES.identite));
    await expect(page.getByLabel('Identifiant unique')).toBeVisible();
    await page.getByLabel('Identifiant unique').fill('agent.casier@sira.example');
    await page.getByRole('button', { name: 'Continuer' }).click();
    await expect(page).toHaveURL(sur(ADRESSES.identite));
    expect(await session(page, ADRESSES.citoyen)).toBe(401);
  });

  test('un agent d’un registre ne peut pas ouvrir la console ni un autre registre', async ({
    page,
  }) => {
    await connecterAgentRegistre(page, 'casier');
    expect(await session(page, ADRESSES.console)).toBe(401);
    // Même royaume, autre application : la session unique ne donne pas le rôle de l'autre registre.
    await page.goto(`${ADRESSES.etatCivil}/api/auth/connexion/sira?retour=/pannes`);
    await page.waitForURL((url) => sur(ADRESSES.etatCivil)(url) || sur(ADRESSES.identite)(url));
    const panne = (
      await page.request.get(`${ADRESSES.etatCivil}/api/panne`, {
        headers: { Accept: 'application/json' },
      })
    ).status();
    expect([401, 403]).toContain(panne);
  });
});
