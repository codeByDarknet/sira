import { Page, devices, expect, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS } from '../../outils/comptes';
import { connecterCitoyen } from '../../outils/connexion';
import { appeler } from '../../outils/navigateur';
import {
  attendreDossier,
  codeDuService,
  deposer,
  lireDemande,
  ouvrirDemande,
  repondre,
} from '../../outils/service';

const TELEPHONE = { ...devices['Pixel 7'], locale: 'fr-FR' };

/** Le citoyen ouvre sur son téléphone la demande que le service affiche par son code court. */
async function ouvrirParCodeCourt(telephone: Page, codeCourt: string): Promise<void> {
  await telephone.getByLabel('Saisir le code').fill(codeCourt);
  await telephone.getByRole('button', { name: 'Ouvrir la demande' }).click();
}

test.describe('Accord donné depuis le téléphone', () => {
  test('Code court affiché par le service, saisi sur le téléphone', async ({ page, browser }) => {
    const demande = await ouvrirDemande(page, 'concours', 'CODE', CITOYENS.mariam.identifiant);
    expect(demande.code_court).toMatch(/^[A-Z]{4}-\d{4}$/);
    await expect(page.getByText(demande.code_court as string)).toBeVisible();
    const code = await codeDuService(page);
    // Lu chiffre à chiffre par les lecteurs d'écran : « 4 9 2 1 1 3 ».
    await expect(page.getByText(code.split('').join(' '))).toBeAttached();

    const contexte = await browser.newContext(TELEPHONE);
    const telephone = await contexte.newPage();
    await connecterCitoyen(telephone, CITOYENS.mariam.identifiant, '/scanner');
    await ouvrirParCodeCourt(telephone, demande.code_court as string);

    // Le code n'est jamais remis au téléphone : le citoyen le recopie depuis l'écran du service.
    await expect(telephone.getByLabel('Code affiché par le service')).toBeVisible();
    await expect(telephone.getByRole('main')).not.toContainText(code);

    await repondre(telephone, 'Autoriser', code);
    await expect(
      telephone.getByRole('heading', { name: 'Vous avez autorisé l’accès' }),
    ).toBeVisible();

    // L'écran du service passe seul à la récupération, puis au formulaire et au dépôt.
    const dossier = await attendreDossier(page, 'concours');
    const depose = await deposer(page, 'concours');
    expect(depose.numero).toBe(dossier.numero);
    await expect(page.getByRole('main')).toContainText(dossier.numero);
    await contexte.close();
  });

  test('Un mauvais code de vérification est refusé, rien n’est transmis', async ({
    page,
    browser,
  }) => {
    const demande = await ouvrirDemande(page, 'concours', 'CODE', CITOYENS.mariam.identifiant);
    const code = await codeDuService(page);
    const faux = String((Number(code) + 1) % 1_000_000).padStart(6, '0');

    const contexte = await browser.newContext(TELEPHONE);
    const telephone = await contexte.newPage();
    await connecterCitoyen(telephone, CITOYENS.mariam.identifiant, '/scanner');
    await ouvrirParCodeCourt(telephone, demande.code_court as string);
    await repondre(telephone, 'Autoriser', faux);

    await expect(telephone.getByText('Votre réponse n’a pas été enregistrée')).toBeVisible();
    await expect(
      telephone.getByRole('heading', { name: 'Un service demande vos informations' }),
    ).toBeVisible();
    const vue = await lireDemande(page);
    expect(vue.etat).toBe('EN_ATTENTE_ACCORD');
    expect(vue.dossier_ouvert).toBe(false);
    expect((await appeler(page, '/api/parcours/dossier')).statut).toBe(404);

    // Le citoyen n'était pas devant ce service : il refuse.
    await repondre(telephone, 'Refuser');
    await expect(
      telephone.getByRole('heading', { name: 'Vous avez refusé la demande' }),
    ).toBeVisible();
    await contexte.close();
  });

  test('Un code court inconnu n’ouvre aucune demande', async ({ page }) => {
    await connecterCitoyen(page, CITOYENS.mariam.identifiant, '/scanner');
    await ouvrirParCodeCourt(page, 'ZZZZ-0000');
    await expect(page.getByText('Ce code n’est pas reconnu, ou il a expiré.')).toBeVisible();
    await expect(page.getByRole('heading', { name: /Un service demande/ })).toBeHidden();
    expect(new URL(page.url()).pathname).not.toMatch(/^\/accord\//);
  });

  test('Demande notifiée par identifiant unique', async ({ page, browser }) => {
    const citoyen = CITOYENS.hamidou;
    const demande = await ouvrirDemande(page, 'bourses', 'NOTIFICATION', citoyen.identifiant);
    expect(demande.code_court, 'aucun code à recopier par ce canal').toBeNull();
    const code = await codeDuService(page);

    const contexte = await browser.newContext(TELEPHONE);
    const telephone = await contexte.newPage();
    await connecterCitoyen(telephone, citoyen.identifiant, '/demandes');
    await expect(telephone.getByRole('heading', { name: 'Demandes en attente' })).toBeVisible();
    await telephone.goto(`${ADRESSES.citoyen}/accord/${demande.id}`);
    await repondre(telephone, 'Autoriser', code);
    await expect(
      telephone.getByRole('heading', { name: 'Vous avez autorisé l’accès' }),
    ).toBeVisible();

    await attendreDossier(page, 'bourses');
    await contexte.close();
  });

  test('La demande notifiée n’apparaît chez aucun autre citoyen', async ({ page, browser }) => {
    const demande = await ouvrirDemande(
      page,
      'bourses',
      'NOTIFICATION',
      CITOYENS.hamidou.identifiant,
    );

    const contexte = await browser.newContext();
    const autre = await contexte.newPage();
    await connecterCitoyen(autre, CITOYENS.aicha.identifiant, '/demandes');
    const lecture = await appeler(autre, `/api/demandes/${demande.id}`);
    expect(lecture.statut).toBe(404);
    const decision = await appeler(autre, `/api/demandes/${demande.id}/decision`, 'POST', {
      decision: 'ACCEPTER',
      code_verification: await codeDuService(page),
    });
    expect(decision.statut).toBe(404);
    await contexte.close();
  });

  test('Un anonyme qui saisit l’identifiant d’autrui n’obtient jamais le dossier', async ({
    page,
    browser,
  }) => {
    // Hamidou a déjà autorisé les bourses (scénario précédent) : son accord ne vaut pas pour un inconnu.
    const citoyen = CITOYENS.hamidou;
    const reponses: string[] = [];
    page.on('response', async (reponse) => {
      if (reponse.url().startsWith(`${ADRESSES.bourses}/api/`)) {
        reponses.push(await reponse.text().catch(() => ''));
      }
    });
    const demande = await ouvrirDemande(page, 'bourses', 'NOTIFICATION', citoyen.identifiant);
    expect(demande.etat, 'jamais accordée d’office').toBe('EN_ATTENTE_ACCORD');
    expect(demande.dossier_ouvert).toBe(false);

    // La vraie personne voit la demande dans son espace, et la refuse.
    const contexte = await browser.newContext(TELEPHONE);
    const telephone = await contexte.newPage();
    await connecterCitoyen(telephone, citoyen.identifiant, `/accord/${demande.id}`);
    await repondre(telephone, 'Refuser');
    await expect(
      telephone.getByRole('heading', { name: 'Vous avez refusé la demande' }),
    ).toBeVisible();
    await contexte.close();

    await expect(async () => {
      expect((await lireDemande(page)).etat).toBe('REFUSEE');
    }).toPass({ timeout: 30_000 });
    expect((await lireDemande(page)).dossier_ouvert).toBe(false);
    expect((await appeler(page, '/api/parcours/dossier')).statut).toBe(404);
    const piece = await appeler(page, '/api/parcours/dossier/pieces/diplomes.attestation');
    expect(piece.statut).toBe(404);
    expect(reponses.join('\n')).not.toMatch(/HAMIDOU|DICKO/i);
  });

  test('Le citoyen refuse, le service ne reçoit rien', async ({ page, browser }) => {
    const demande = await ouvrirDemande(page, 'concours', 'CODE', CITOYENS.rasmata.identifiant);

    const contexte = await browser.newContext(TELEPHONE);
    const telephone = await contexte.newPage();
    await connecterCitoyen(telephone, CITOYENS.rasmata.identifiant, '/scanner');
    await ouvrirParCodeCourt(telephone, demande.code_court as string);
    await repondre(telephone, 'Refuser');
    await expect(
      telephone.getByRole('heading', { name: 'Vous avez refusé la demande' }),
    ).toBeVisible();

    await expect(async () => {
      expect((await lireDemande(page)).etat).toBe('REFUSEE');
    }).toPass({ timeout: 30_000 });
    expect((await lireDemande(page)).dossier_ouvert).toBe(false);
    expect((await appeler(page, '/api/parcours/dossier')).statut).toBe(404);

    await telephone.goto(`${ADRESSES.citoyen}/historique`);
    await expect(telephone.getByRole('main').getByText('Refusé par vous').first()).toBeVisible();
    await contexte.close();
  });
});
