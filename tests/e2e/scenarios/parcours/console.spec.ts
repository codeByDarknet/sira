import { Locator, Page, expect, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS, dateNaissance } from '../../outils/comptes';
import { connecterAdministrateur } from '../../outils/connexion';
import { appeler } from '../../outils/navigateur';

/** Liste déroulante PrimeNG : ouvrir, puis cliquer l'option voulue (rendue dans le corps de la page). */
async function choisir(page: Page, liste: Locator, option: string): Promise<void> {
  await liste.click();
  await page.getByRole('option', { name: option, exact: true }).click();
}

test.describe('Console du socle', () => {
  test.setTimeout(300_000);

  test('question créée sans code, essayée, habilitée par deux comptes, journal intact', async ({
    page,
    browser,
  }) => {
    // Un code neuf à chaque passage : une question publiée ne s'efface pas.
    const suffixe = Date.now().toString(36);
    // La politique du registre d'état civil n'admet que les seuils légaux (18 et 35 ans).
    const libelle = `Vous avez 18 ans ou plus (${suffixe})`;
    const fenetre = page.getByRole('dialog');
    const continuer = fenetre.getByRole('button', { name: 'Continuer' });

    // Ajouter et modifier se font dans une fenêtre, ouverte depuis la liste.
    await connecterAdministrateur(page, 'instructeur', '/questions');
    await page.getByRole('button', { name: 'Créer une question' }).first().click();
    await fenetre.getByRole('radio', { name: 'État civil' }).check();
    await continuer.click();
    await fenetre.getByRole('radio', { name: /Oui ou non/ }).check();
    await continuer.click();
    await fenetre.getByLabel('Information à vérifier').fill('date_naissance');
    await choisir(page, fenetre.getByLabel('Condition'), 'Âge au moins');
    await fenetre.getByLabel('Âge minimal, en années').fill('18');
    await expect(fenetre.getByText('Ce que le service recevra')).toBeVisible();
    await continuer.click();
    await fenetre.getByLabel('Libellé affiché au citoyen').fill(libelle);
    await fenetre.getByLabel('Code de la question').fill(`age-18-${suffixe}`); // l'écran ajoute « etat-civil. »
    await continuer.click();

    await fenetre
      .getByLabel('Identifiant unique d’une personne fictive')
      .fill(CITOYENS.aicha.identifiant);
    await fenetre.getByRole('button', { name: 'Lancer l’essai' }).click();
    const essai = fenetre.getByText('Résultat de l’essai').locator('..');
    await expect(essai).toContainText(/oui/i);
    await expect(essai).not.toContainText(dateNaissance(CITOYENS.aicha));
    await fenetre.getByRole('button', { name: 'Publier la question' }).click();
    await expect(page.getByText(`Question « ${libelle} » publiée.`)).toBeVisible();

    // Une seule habilitation en vigueur par démarche : celle de l'amorçage reste intacte, la
    // question publiée est autorisée pour une démarche neuve.
    const demarche = `concours-essai-${suffixe}`;
    const nomDemarche = `Concours de démonstration (${suffixe})`;
    await page.goto(`${ADRESSES.console}/demarches`);
    await page.getByRole('button', { name: 'Ajouter une démarche' }).first().click();
    await fenetre.getByLabel('Code').fill(demarche);
    await fenetre.getByLabel('Nom de la démarche').fill(nomDemarche);
    await choisir(
      page,
      fenetre.getByLabel('Service qui rend la démarche'),
      'Concours directs 2026',
    );
    await fenetre
      .getByLabel('Finalité')
      .fill('Vérifier que vous remplissez les conditions du concours de démonstration.');
    await fenetre.getByLabel('Base légale').fill('Texte de démonstration');
    await fenetre.getByLabel('Durée de l’accord, en jours').fill('30');
    await fenetre.getByRole('button', { name: 'Ajouter la démarche' }).click();
    await expect(page.getByText(`Démarche « ${nomDemarche} » ajoutée.`)).toBeVisible();

    await page.goto(`${ADRESSES.console}/habilitations`);
    await page.getByRole('button', { name: 'Préparer une habilitation' }).first().click();
    await choisir(page, fenetre.getByLabel('Service qui demande'), 'Concours directs 2026');
    await choisir(page, fenetre.getByLabel('Démarche'), nomDemarche);
    await fenetre.getByRole('checkbox', { name: new RegExp(suffixe) }).check();
    await fenetre
      .getByLabel('Justification')
      .fill('Le concours de démonstration exige la majorité, vérifiée une seconde fois.');
    await fenetre.getByRole('button', { name: 'Enregistrer le brouillon' }).click();
    await page.waitForURL(/\/habilitations\/[0-9a-f-]{36}$/);
    const id = new URL(page.url()).pathname.split('/').pop() as string;

    await page.getByRole('button', { name: 'Soumettre à l’autorité' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Soumettre à l’autorité' }).click();

    // Séparation des rôles : celui qui instruit ne décide pas, ni à l'écran ni par l'API.
    await expect(page.getByRole('button', { name: 'Approuver' })).toHaveCount(0);
    const forcee = await appeler(page, `/api/habilitations/${id}/approbation`, 'POST');
    expect(forcee.statut).toBe(403);

    const contexteAutorite = await browser.newContext();
    const autorite = await contexteAutorite.newPage();
    await connecterAdministrateur(autorite, 'autorite', `/habilitations/${id}`);
    await autorite.getByRole('button', { name: 'Approuver' }).click();
    await autorite.getByRole('dialog').getByRole('button', { name: 'Approuver' }).click();
    await expect(async () => {
      const fiche = await appeler<{ etat: string; instruite_par: string; decidee_par: string }>(
        autorite,
        `/api/habilitations/${id}`,
      );
      expect(fiche.corps.etat).toBe('APPROUVEE');
      expect(fiche.corps.decidee_par).not.toBe(fiche.corps.instruite_par);
    }).toPass();

    await autorite.goto(`${ADRESSES.console}/journal`);
    await autorite.getByRole('button', { name: 'Vérifier l’intégrité' }).click();
    await expect(autorite.getByText('Le journal est intact')).toBeVisible({ timeout: 60_000 });
    await contexteAutorite.close();
  });

  test('une question citée par une habilitation approuvée ne se modifie plus', async ({ page }) => {
    const code = 'etat-civil.majeur';
    await connecterAdministrateur(page, 'instructeur', `/questions/${code}`);
    await page.getByRole('button', { name: 'Modifier' }).click();
    const fenetre = page.getByRole('dialog');
    const continuer = fenetre.getByRole('button', { name: 'Continuer' });
    await continuer.click();
    await continuer.click();
    await continuer.click();
    await fenetre.getByLabel('Libellé affiché au citoyen').fill('Vous êtes majeur, ou presque');
    await continuer.click();
    await fenetre.getByRole('button', { name: 'Publier la question' }).click();
    await expect(fenetre.getByText('La question n’a pas été publiée')).toBeVisible();

    // Ni par l'écran ni par l'API : le libellé que le citoyen a lu reste celui de son accord.
    const jeu = await appeler<Record<string, unknown>>(page, `/api/jeux/${code}`);
    expect(jeu.corps.libelle).toBe('Vous êtes majeur');
    const forcee = await appeler(page, `/api/jeux/${code}`, 'PUT', {
      ...jeu.corps,
      libelle: 'Vous êtes majeur, ou presque',
    });
    expect(forcee.statut).toBe(409);
    expect((await appeler<{ libelle: string }>(page, `/api/jeux/${code}`)).corps.libelle).toBe(
      'Vous êtes majeur',
    );
  });

  test('l’auditeur lit, mais n’écrit pas', async ({ page }) => {
    await connecterAdministrateur(page, 'auditeur', '/habilitations');
    await expect(page.getByRole('heading', { name: 'Habilitations', level: 1 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Préparer une habilitation' })).toHaveCount(0);
    const ecriture = await appeler(page, '/api/habilitations', 'POST');
    expect(ecriture.statut).toBe(403);
  });
});
