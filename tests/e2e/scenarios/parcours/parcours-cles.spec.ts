import { readFile } from 'node:fs/promises';
import { Page, expect, test } from '@playwright/test';
import { ADRESSES, sur } from '../../outils/adresses';
import { CITOYENS, dateNaissance } from '../../outils/comptes';
import { connecterAgentService } from '../../outils/connexion';
import { appeler, ecouterReponses } from '../../outils/navigateur';
import { passerEn } from '../../outils/registre';
import {
  Dossier,
  attendreDossier,
  attendreEcranAccord,
  deposer,
  inscrireParRedirection,
  lireDemande,
  ouvrirDemande,
  repondre,
  verification,
} from '../../outils/service';

const SERVICE = 'Concours directs 2026';

test.describe('Minimisation : ne transmettre que l’utile', () => {
  test('le concours apprend « majeur : oui », jamais la date de naissance', async ({
    page,
    browser,
  }) => {
    const citoyen = CITOYENS.aicha;
    const naissance = dateNaissance(citoyen);
    const reponsesDuService = ecouterReponses(page, ADRESSES.concours);

    const dossier = await inscrireParRedirection(page, 'concours', citoyen);

    const majeur = verification(dossier, 'etat-civil.majeur');
    expect(majeur.niveau).toBe('STATUT');
    expect(majeur.valeur).toBe(true);
    expect(majeur.champs ?? {}).toEqual({});
    // Le formulaire prérempli coche « Oui », verrouillé ; la date de naissance n'y est pas.
    const majorite = page.getByRole('group', { name: 'Vous êtes majeur' });
    await expect(majorite.getByRole('radio', { name: 'Oui' })).toBeChecked();
    await expect(majorite.getByRole('radio', { name: 'Oui' })).toBeDisabled();
    await expect(page.getByRole('main')).not.toContainText(naissance);
    await expect(page.locator(`input[value="${naissance}"]`)).toHaveCount(0);

    const recu = (await reponsesDuService()).join('\n');
    expect(recu).toContain('etat-civil.majeur');
    expect(recu).not.toContain(naissance);
    expect(recu).not.toContain('date_naissance');

    // L'agent du service n'en voit pas davantage que le citoyen.
    const contexteAgent = await browser.newContext();
    const agent = await contexteAgent.newPage();
    await connecterAgentService(agent, 'concours');
    const liste = await appeler<{ elements: { id: string; numero: string }[] }>(
      agent,
      '/api/agent/dossiers?taille=100',
    );
    const ligne = liste.corps.elements.find((d) => d.numero === dossier.numero);
    expect(ligne, 'l’agent retrouve le dossier').toBeDefined();
    const detail = await appeler(agent, `/api/agent/dossiers/${ligne?.id}`);
    expect(detail.statut).toBe(200);
    expect(detail.texte).not.toContain(naissance);
    expect(detail.texte).not.toContain(citoyen.identifiant);
    await contexteAgent.close();
  });
});

test.describe('Transparence : le citoyen voit qui a consulté', () => {
  test('l’historique liste les consultations, puis l’accès est retiré', async ({ page }) => {
    await inscrireParRedirection(page, 'concours', CITOYENS.bakary);

    await page.goto(`${ADRESSES.citoyen}/historique`);
    await expect(
      page.getByRole('heading', { name: 'Qui a consulté mes informations' }),
    ).toBeVisible();
    const principal = page.getByRole('main');
    await expect(principal).toContainText(SERVICE);
    await expect(principal).toContainText('État civil');
    await expect(principal).toContainText('Vous êtes majeur');
    await expect(
      principal.getByRole('cell', { name: 'Transmis', exact: true }).first(),
    ).toBeVisible();
    await expect(
      principal.getByRole('cell', { name: 'Autorisé par vous', exact: true }).first(),
    ).toBeVisible();

    await page.goto(`${ADRESSES.citoyen}/acces`);
    await page
      .getByRole('button', { name: `Retirer l’accès de ${SERVICE}` })
      .first()
      .click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Retirer l’accès', exact: true })
      .click();
    await expect(page.getByText(`L’accès de ${SERVICE} est retiré`)).toBeVisible();

    const actives = await appeler<{ elements: { service: { code: string } }[] }>(
      page,
      '/api/autorisations?etat=ACTIVE',
    );
    expect(actives.corps.elements.filter((a) => a.service.code === 'concours')).toEqual([]);

    await page.goto(`${ADRESSES.citoyen}/historique`);
    await expect(page.getByRole('main').getByText('Accès retiré par vous').first()).toBeVisible();

    // Une nouvelle démarche après le retrait redemande l'accord : rien ne part sans lui.
    await ouvrirDemande(page, 'concours', 'REDIRECTION', CITOYENS.bakary.identifiant);
    await attendreEcranAccord(page);
    await repondre(page, 'Refuser');
    await page.waitForURL(sur(ADRESSES.concours));
    await expect(async () => {
      expect((await lireDemande(page)).etat).toBe('REFUSEE');
    }).toPass({ timeout: 30_000 });
    expect((await appeler(page, '/api/parcours/dossier')).statut).toBe(404);
  });
});

test.describe('Bourses : des champs et une pièce, reçus sans rien fournir', () => {
  test('le service reçoit l’identité, le baccalauréat et l’attestation en PDF', async ({
    page,
  }) => {
    const citoyen = CITOYENS.aicha;
    const dossier = await inscrireParRedirection(page, 'bourses', citoyen);

    const identite = verification(dossier, 'etat-civil.identite');
    expect(identite.niveau).toBe('CHAMPS');
    expect(identite.champs).toMatchObject({
      nom: 'KABORÉ',
      prenoms: 'Wendkuni Aïcha',
      date_naissance: dateNaissance(citoyen),
    });
    expect(verification(dossier, 'nationalite.burkinabe').valeur).toBe(true);
    expect(verification(dossier, 'diplomes.baccalaureat').valeur).toBe(true);
    const attestation = verification(dossier, 'diplomes.attestation');
    expect(attestation.niveau).toBe('DOCUMENT');
    expect(attestation.piece?.type).toBe('application/pdf');

    // Le formulaire revient prérempli et verrouillé : les champs se lisent en clair.
    const principal = page.getByRole('main');
    await expect(principal.getByLabel('Nom', { exact: true })).toHaveValue('KABORÉ');
    await expect(principal.getByLabel('Nom', { exact: true })).toBeDisabled();
    await expect(principal.getByLabel('Prénoms', { exact: true })).toHaveValue('Wendkuni Aïcha');
    await expect(principal.getByLabel('Date de naissance', { exact: true })).toHaveValue(
      '14 mars 2003',
    );

    // La pièce reçue du registre : aperçu dans le navigateur, ou téléchargement.
    // L'aperçu s'ouvre dans une fenêtre de la page : pdf.js lit la pièce à l'adresse d'aperçu.
    const lecture = page.waitForResponse((reponse) => /apercu/.test(reponse.url()));
    await principal
      .getByRole('button', { name: /Prévisualiser/ })
      .first()
      .click();
    const adresseApercu = (await lecture).url();
    const affichee = await page.request.get(adresseApercu);
    await page.keyboard.press('Escape');
    expect(affichee.status()).toBe(200);
    expect(affichee.headers()['content-type']).toBe('application/pdf');
    expect(affichee.headers()['content-disposition']).toMatch(/^inline/);
    expect(affichee.headers()['x-content-type-options']).toBe('nosniff');
    expect((await affichee.body()).subarray(0, 5).toString('latin1')).toBe('%PDF-');

    const telechargement = page.waitForEvent('download');
    await principal.getByRole('link', { name: /Télécharger/ }).click();
    const fichier = await telechargement;
    expect(fichier.suggestedFilename()).toMatch(/\.pdf$/);
    const contenu = await readFile(await fichier.path());
    expect(contenu.subarray(0, 5).toString('latin1')).toBe('%PDF-');
    const brute = await page.request.get(
      `${ADRESSES.bourses}/api/parcours/dossier/pieces/diplomes.attestation`,
    );
    expect(brute.status()).toBe(200);
    expect(brute.headers()['content-type']).toBe('application/pdf');
    expect(brute.headers()['content-disposition']).toMatch(/^attachment/);

    // Une autre session (un autre navigateur) n'atteint pas la pièce.
    const etranger = await page.context().browser()?.newContext();
    if (etranger) {
      const lecture = await etranger.request.get(adresseApercu);
      expect(lecture.status()).toBe(404);
      await etranger.close();
    }

    await deposer(page, 'bourses');
    await expect(principal.getByText('Les pièces que vous n’avez pas eu à fournir')).toBeVisible();
  });
});

test.describe('Résilience : tenir quand un registre tombe', () => {
  test.setTimeout(300_000);

  /**
   * Une attestation du casier reste valable 30 jours : le passage suivant la réutiliserait sans
   * interroger le registre, et la panne ne servirait à rien. Chaque passage prend donc le
   * premier de ces citoyens dont le casier n'a pas encore été transmis au concours.
   */
  const CANDIDATS = [CITOYENS.dieudonne, CITOYENS.aicha, CITOYENS.hamidou, CITOYENS.rasmata];

  test('casier muet : dossier enregistré et partiel, puis complété tout seul', async ({
    browser,
  }) => {
    const contexteAgent = await browser.newContext();
    const agent = await contexteAgent.newPage();
    try {
      await passerEn(agent, 'casier', 'Muet');

      let page: Page | undefined;
      let dossier: Dossier | undefined;
      for (const citoyen of CANDIDATS) {
        const contexte = await browser.newContext();
        const essai = await contexte.newPage();
        const recu = await inscrireParRedirection(essai, 'concours', citoyen);
        if (verification(recu, 'casier.neant').etat !== 'REUTILISE') {
          [page, dossier] = [essai, recu];
          break;
        }
        await contexte.close();
      }
      if (!page || !dossier) {
        throw new Error(
          'Tous les candidats ont déjà un casier transmis : scripts/sira reinitialiser',
        );
      }

      expect(dossier.numero, 'le dossier est enregistré malgré la panne').toBeTruthy();
      expect(dossier.etat).toBe('EN_VERIFICATION');
      expect(verification(dossier, 'casier.neant').etat).toBe('A_TRAITER');
      expect(['OBTENU', 'REUTILISE']).toContain(verification(dossier, 'etat-civil.majeur').etat);
      const service = page;
      await expect(async () => {
        expect((await lireDemande(service)).etat).toBe('PARTIELLE');
      }).toPass({ timeout: 60_000 });
      // Le dossier se dépose sans attendre le casier ; l'écran le dit en attente.
      const depose = await deposer(page, 'concours');
      expect(depose.numero).toBe(dossier.numero);
      await expect(page.getByRole('main')).toContainText(dossier.numero);
      await expect(page.getByText('Votre dossier est enregistré')).toBeVisible();

      await passerEn(agent, 'casier', 'Normal');

      // Personne ne relance rien : Sira retente seul, le service est prévenu.
      let complete: Dossier | undefined;
      await expect(async () => {
        complete = await attendreDossier(service, 'concours');
        expect(verification(complete, 'casier.neant').etat).toBe('OBTENU');
      }).toPass({ timeout: 180_000, intervals: [5_000] });
      expect(complete?.numero).toBe(dossier.numero);
      expect(complete?.etat).not.toBe('EN_VERIFICATION');
      expect((await lireDemande(page)).etat).toBe('COMPLETE');
    } finally {
      // Le registre est partagé avec les autres scénarios : il repart toujours en état normal.
      await passerEn(agent, 'casier', 'Normal');
      await contexteAgent.close();
    }
  });
});
