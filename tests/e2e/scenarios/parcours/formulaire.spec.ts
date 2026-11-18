import { Page, devices, expect, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS, dateNaissance } from '../../outils/comptes';
import { connecterAgentService, connecterCitoyen } from '../../outils/connexion';
import { appeler, ecouterReponses } from '../../outils/navigateur';
import {
  DossierComplet,
  MODALE,
  attendreDossier,
  attendreEcranAccord,
  deposer,
  inscrireParRedirection,
  lireDemande,
  ouvrirDemande,
  remplirDemande,
  verification,
} from '../../outils/service';

const TELEPHONE = { ...devices['Pixel 7'], locale: 'fr-FR' };

/** Un PDF minimal, mais complet : en-tête, une page vide, table des objets et fin de fichier. */
const PDF = Buffer.from(
  [
    '%PDF-1.4',
    '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
    '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj',
    '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj',
    'trailer << /Root 1 0 R >>',
    '%%EOF',
  ].join('\n'),
  'latin1',
);

/**
 * Envoie un fichier brut au portail du service, depuis la page (mêmes cookies, jeton XSRF), comme
 * le fait l'écran « Votre dossier ».
 */
async function envoyerPiece(
  page: Page,
  code: string,
  contenu: Buffer,
  type: string,
): Promise<number> {
  return page.evaluate(
    async ([chemin, octets, typeContenu]) => {
      const jeton = /(?:^|; )XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1] ?? '';
      const reponse = await fetch(chemin, {
        method: 'PUT',
        headers: { 'Content-Type': typeContenu, 'X-XSRF-TOKEN': decodeURIComponent(jeton) },
        body: new Uint8Array(octets),
      });
      return reponse.status;
    },
    [`/api/parcours/dossier/declarations/${code}/piece?nom=essai.pdf`, [...contenu], type] as const,
  );
}

test.describe('Le formulaire de la démarche', () => {
  test('l’identifiant d’un autre citoyen : l’accord est introuvable, le service ne reçoit rien', async ({
    page,
    browser,
  }) => {
    // Mariam est connectée à son espace, mais saisit l'identifiant de Dieudonné.
    const connecte = CITOYENS.mariam;
    const saisi = CITOYENS.dieudonne;
    await connecterCitoyen(page, connecte.identifiant);
    const demande = await ouvrirDemande(page, 'concours', 'REDIRECTION', saisi.identifiant);
    await attendreEcranAccord(page);
    await expect(
      page.getByRole('heading', { name: 'Cette demande est introuvable' }),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Autoriser' })).toHaveCount(0);

    // Ni lecture, ni rattachement, ni décision : pour elle, la demande n'existe pas.
    expect((await appeler(page, `/api/demandes/${demande.id}`)).statut).toBe(404);
    const decision = await appeler(page, `/api/demandes/${demande.id}/decision`, 'POST', {
      decision: 'ACCEPTER',
    });
    expect(decision.statut).toBe(404);

    // Le service attend toujours un accord, et n'a ouvert aucun dossier.
    await page.goto(ADRESSES.concours);
    const vue = await lireDemande(page);
    expect(vue.etat).toBe('EN_ATTENTE_ACCORD');
    expect(vue.dossier_ouvert).toBe(false);
    expect((await appeler(page, '/api/parcours/dossier')).statut).toBe(404);

    // Par code court, même chose : le code d'une demande qui désigne Dieudonné est faux pour elle.
    const contexte = await browser.newContext();
    const service = await contexte.newPage();
    const parCode = await ouvrirDemande(service, 'concours', 'CODE', saisi.identifiant);
    const telephone = await (await browser.newContext(TELEPHONE)).newPage();
    await connecterCitoyen(telephone, connecte.identifiant, '/scanner');
    await telephone.getByLabel('Saisir le code').fill(parCode.code_court as string);
    await telephone.getByRole('button', { name: 'Ouvrir la demande' }).click();
    await expect(telephone.getByText('Ce code n’est pas reconnu, ou il a expiré.')).toBeVisible();
    expect((await lireDemande(service)).dossier_ouvert).toBe(false);
    await telephone.context().close();
    await contexte.close();
  });

  test('le formulaire revient prérempli et verrouillé, la personne complète et dépose', async ({
    page,
    browser,
  }) => {
    test.setTimeout(120_000);
    // Bakary : aucun autre scénario ne dépose son dossier au concours.
    const citoyen = CITOYENS.bakary;
    const naissance = dateNaissance(citoyen);
    const reponsesDuService = ecouterReponses(page, ADRESSES.concours);

    // Avant l'accord : un formulaire vide où chaque information dit quel registre la remplira.
    await remplirDemande(page, 'concours', citoyen.identifiant);
    const modale = page.getByRole('dialog', { name: MODALE });
    await expect(modale).toContainText('Votre casier judiciaire est vierge');
    await expect(modale).toContainText('registre Casier judiciaire');
    await modale.getByRole('button', { name: 'Annuler' }).click();
    await expect(page.getByLabel('Nom', { exact: true })).toBeDisabled();
    await expect(page.getByLabel('Nom', { exact: true })).toHaveValue('');

    const recu = await inscrireParRedirection(page, 'concours', citoyen);

    // Le concours reçoit le nom et les prénoms, jamais la date de naissance.
    const nom = verification(recu, 'etat-civil.nom-prenoms');
    expect(nom.niveau).toBe('CHAMPS');
    expect(nom.champs).toEqual({ nom: 'SANOU', prenoms: 'Bakary Désiré' });
    const echanges = (await reponsesDuService()).join('\n');
    expect(echanges).not.toContain(naissance);
    expect(echanges).not.toContain('date_naissance');
    expect(echanges).not.toContain(citoyen.identifiant);

    // L'écran verrouille ce qui vient des registres.
    const principal = page.getByRole('main');
    await expect(principal.getByLabel('Nom', { exact: true })).toHaveValue('SANOU');
    await expect(principal.getByLabel('Nom', { exact: true })).toBeDisabled();
    await expect(principal.getByLabel('Prénoms', { exact: true })).toBeDisabled();
    const casier = page.getByRole('group', { name: 'Votre casier judiciaire est vierge' });
    // Son bulletin est vierge : « Oui », coché par le registre, que la personne ne change pas.
    await expect(casier.getByRole('radio', { name: 'Oui' })).toBeChecked();
    await expect(casier.getByRole('radio', { name: 'Oui' })).toBeDisabled();
    await expect(casier.getByRole('radio', { name: 'Non' })).toBeDisabled();

    // Le serveur refuse lui aussi d'y toucher, par saisie comme par pièce.
    const ecrire = await appeler(
      page,
      '/api/parcours/dossier/declarations/etat-civil.nom-prenoms',
      'PUT',
      { valeur: 'AUTRE' },
    );
    expect(ecrire.statut).toBe(409);
    expect(await envoyerPiece(page, 'casier.neant', PDF, 'application/pdf')).toBe(409);
    expect(
      (await appeler(page, '/api/parcours/dossier/declarations/inconnu', 'PUT', { valeur: 'x' }))
        .statut,
    ).toBe(404);

    // Un fichier qui n'est pas un PDF est refusé, à l'écran comme par le serveur.
    // Un passage précédent a pu laisser un CV avant d'échouer : la personne peut le retirer.
    const retirer = principal.getByRole('button', { name: 'Retirer Curriculum vitæ' });
    if (await retirer.isVisible()) {
      await retirer.click();
    }
    const cv = principal.getByLabel('Curriculum vitæ');
    await cv.setInputFiles({ name: 'cv.txt', mimeType: 'text/plain', buffer: Buffer.from('cv') });
    await expect(principal.getByText('Choisissez un fichier PDF.')).toBeVisible();
    expect(
      await envoyerPiece(page, 'cv', Buffer.from('ceci n’est pas un PDF'), 'application/pdf'),
    ).toBe(400);
    expect(await envoyerPiece(page, 'cv', PDF, 'text/plain')).toBe(415);

    // La personne complète ce que les registres ne donnent pas : son CV et son téléphone.
    await cv.setInputFiles({ name: 'mon-cv.pdf', mimeType: 'application/pdf', buffer: PDF });
    await expect(
      principal.getByRole('paragraph').filter({ hasText: 'Curriculum vitæ — mon-cv.pdf' }),
    ).toBeVisible();
    // L'aperçu s'ouvre dans une fenêtre de la page : pdf.js lit la pièce à l'adresse d'aperçu.
    const lecture = page.waitForResponse((reponse) => /apercu/.test(reponse.url()));
    await principal.getByRole('button', { name: 'Prévisualiser Curriculum vitæ' }).click();
    const lu = await page.request.get((await lecture).url());
    expect(lu.status()).toBe(200);
    expect(lu.headers()['content-type']).toBe('application/pdf');
    expect(lu.headers()['content-disposition']).toMatch(/^inline/);
    expect(Buffer.from(await lu.body()).equals(PDF)).toBe(true);

    await principal.getByLabel('Téléphone de contact').fill('70 00 00 00');
    await principal.getByRole('button', { name: 'Enregistrer Téléphone de contact' }).click();
    await expect(principal.getByText('Enregistré', { exact: true })).toBeVisible();

    const depose: DossierComplet = await deposer(page, 'concours');
    expect(depose.declarations.map((d) => d.code).sort()).toEqual(['cv', 'telephone']);
    await expect(principal.getByRole('heading', { name: 'Déclaré par vous' })).toBeVisible();

    // Après le dépôt, ce qui a été déclaré ne change plus.
    const apres = await appeler(page, '/api/parcours/dossier/declarations/telephone', 'PUT', {
      valeur: '71 00 00 00',
    });
    expect(apres.statut).toBe(409);
    expect(await envoyerPiece(page, 'cv', PDF, 'application/pdf')).toBe(409);

    // L'agent voit les deux sections et lit la pièce déclarée, sans l'identifiant du citoyen.
    const contexteAgent = await browser.newContext();
    const agent = await contexteAgent.newPage();
    await connecterAgentService(agent, 'concours');
    const liste = await appeler<{ elements: { id: string; numero: string }[] }>(
      agent,
      '/api/agent/dossiers?taille=100',
    );
    const ligne = liste.corps.elements.find((d) => d.numero === depose.numero);
    expect(ligne, 'l’agent retrouve le dossier').toBeDefined();
    const detail = await appeler(agent, `/api/agent/dossiers/${ligne?.id}`);
    expect(detail.texte).toContain('SANOU');
    expect(detail.texte).not.toContain(citoyen.identifiant);
    expect(detail.texte).not.toContain(naissance);
    const piece = await agent.request.get(
      `${ADRESSES.concours}/api/agent/dossiers/${ligne?.id}/declarations/cv/piece?apercu=true`,
    );
    expect(piece.status()).toBe(200);
    expect(piece.headers()['content-type']).toBe('application/pdf');
    await agent.goto(`${ADRESSES.concours}/agent/${ligne?.id}`);
    await expect(agent.getByRole('heading', { name: 'Vérifié par les registres' })).toBeVisible();
    await expect(agent.getByRole('heading', { name: 'Déclaré par l’usager' })).toBeVisible();
    await contexteAgent.close();

    // Le citoyen, lui, ne lit pas les pièces d'un dossier par la route de l'agent.
    const intrus = await page.request.get(
      `${ADRESSES.concours}/api/agent/dossiers/${ligne?.id}/declarations/cv/piece`,
    );
    expect(intrus.status()).toBe(401);
    expect((await attendreDossier(page, 'concours')).numero).toBe(depose.numero);
  });
});
