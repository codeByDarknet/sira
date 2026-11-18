import { Page, expect } from '@playwright/test';
import { Registre } from './adresses';
import { connecterAgentRegistre } from './connexion';

export type Mode = 'Normal' | 'Lent' | 'En erreur' | 'Muet';

/** Écran « Pannes » du back-office d'un registre simulé : met le registre dans ce mode. */
export async function passerEn(page: Page, registre: Registre, mode: Mode): Promise<void> {
  await connecterAgentRegistre(page, registre, '/pannes');
  await expect(page.getByRole('heading', { name: 'Pannes', level: 1 })).toBeVisible();
  const actuel = page.getByText(`État actuel : ${mode}`);
  await expect(page.getByText(/État actuel :/)).toBeVisible();
  if (!(await actuel.isVisible())) {
    await page.getByRole('button', { name: `Passer en « ${mode} »` }).click();
  }
  await expect(actuel).toBeVisible();
}
