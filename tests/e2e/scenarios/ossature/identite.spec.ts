import { expect, test } from '@playwright/test';
import { ADRESSES, sur } from '../../outils/adresses';
import { CITOYENS } from '../../outils/comptes';
import {
  saisirConnexionAdministrateur,
  saisirConnexionAgent,
  saisirConnexionCitoyen,
} from '../../outils/connexion';
import { attendreCode } from '../../outils/messagerie';
import { adresseAutorisation, retourDe, suivreRetours } from '../../outils/oidc';

/**
 * Les trois écrans de connexion, éprouvés sans les applications : la connexion réussit quand le
 * navigateur repart vers l'application avec un code d'autorisation.
 */
let attendreRetour: (application: string) => Promise<void>;

test.beforeEach(({ page }) => {
  attendreRetour = suivreRetours(page);
});

test.describe('Connexion du citoyen', () => {
  const retour = retourDe(ADRESSES.citoyen);

  test('identifiant unique puis code reçu sur le téléphone', async ({ page }) => {
    await page.goto(adresseAutorisation('sira-citoyens', 'espace-citoyen', retour));
    await saisirConnexionCitoyen(page, CITOYENS.aicha.identifiant);
    await attendreRetour(ADRESSES.citoyen);
  });

  test('un code faux est refusé', async ({ page }) => {
    await page.goto(adresseAutorisation('sira-citoyens', 'espace-citoyen', retour));
    await page.getByLabel('Identifiant unique').fill(CITOYENS.bakary.identifiant);
    const code = await attendreCode(CITOYENS.bakary.identifiant, () =>
      page.getByRole('button', { name: 'Continuer' }).click(),
    );
    const faux = code === '000000' ? '000001' : '000000';
    await page.getByLabel('Code à 6 chiffres reçu par message').fill(faux);
    await page.getByRole('button', { name: 'Valider' }).click();
    await expect(page.getByText('Code incorrect.')).toBeVisible();
    expect(sur(ADRESSES.identite)(new URL(page.url()))).toBe(true);
  });

  test('un identifiant inconnu ne se distingue pas d’un connu', async ({ page }) => {
    await page.goto(adresseAutorisation('sira-citoyens', 'espace-citoyen', retour));
    await page.getByLabel('Identifiant unique').fill('990000000000');
    await page.getByRole('button', { name: 'Continuer' }).click();
    await expect(page.getByLabel('Code à 6 chiffres reçu par message')).toBeVisible();
    await page.getByLabel('Code à 6 chiffres reçu par message').fill('000000');
    await page.getByRole('button', { name: 'Valider' }).click();
    await expect(page.getByText('Code incorrect.')).toBeVisible();
  });

  test('une adresse de retour étrangère à l’espace citoyen est refusée', async ({ page }) => {
    await page.goto(
      adresseAutorisation('sira-citoyens', 'espace-citoyen', retourDe(ADRESSES.concours)),
    );
    await expect(page.getByLabel('Identifiant unique')).toBeHidden();
  });
});

test.describe('Séparation des populations au fournisseur d’identité', () => {
  test('un administrateur du socle n’existe pas chez les citoyens', async ({ page }) => {
    await page.goto(
      adresseAutorisation('sira-citoyens', 'espace-citoyen', retourDe(ADRESSES.citoyen)),
    );
    await page.getByLabel('Identifiant unique').fill('instructeur@sira.example');
    await page.getByRole('button', { name: 'Continuer' }).click();
    await page.getByLabel('Code à 6 chiffres reçu par message').fill('000000');
    await page.getByRole('button', { name: 'Valider' }).click();
    await expect(page.getByText('Code incorrect.')).toBeVisible();
  });

  test('un agent d’administration n’entre pas dans le royaume du socle', async ({ page }) => {
    await page.goto(adresseAutorisation('sira-socle', 'console', retourDe(ADRESSES.console)));
    await saisirConnexionAgent(page, 'casier');
    await expect(page.getByLabel('Mot de passe', { exact: true })).toBeVisible();
    expect(sur(ADRESSES.identite)(new URL(page.url()))).toBe(true);
  });
});

test('Connexion d’un administrateur du socle : mot de passe puis code TOTP', async ({ page }) => {
  await page.goto(adresseAutorisation('sira-socle', 'console', retourDe(ADRESSES.console)));
  await saisirConnexionAdministrateur(page, 'auditeur');
  await attendreRetour(ADRESSES.console);
});

test('Connexion d’un agent de démonstration : mot de passe', async ({ page }) => {
  await page.goto(adresseAutorisation('demo-agents', 'demo-casier', retourDe(ADRESSES.casier)));
  await saisirConnexionAgent(page, 'casier');
  await attendreRetour(ADRESSES.casier);
});
