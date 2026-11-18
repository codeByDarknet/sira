import { expect, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS, dateNaissance } from '../../outils/comptes';
import { connecterAgentService } from '../../outils/connexion';
import { appeler, ecouterReponses } from '../../outils/navigateur';
import { deposer, inscrireParRedirection, verification } from '../../outils/service';

const SERVICE = 'Créer son entreprise';
const JEUX = [
  'casier.neant',
  'etat-civil.extrait-naissance',
  'etat-civil.identite',
  'nationalite.burkinabe',
];

test.describe('Troisième démarche : immatriculer un commerçant sans aucune pièce', () => {
  test('les trois registres répondent, le service ne reçoit que l’utile', async ({
    page,
    browser,
  }) => {
    const citoyen = CITOYENS.rasmata;
    const naissance = dateNaissance(citoyen);
    const reponsesDuService = ecouterReponses(page, ADRESSES.entreprises);

    // Avant de commencer, la démarche dit ce qu'elle vérifiera et les pièces qu'elle évite.
    await page.goto(ADRESSES.entreprises);
    const accueil = page.getByRole('main');
    await expect(
      page.getByRole('heading', { level: 1, name: /Immatriculation d.un commerçant/ }),
    ).toBeVisible();
    await expect(accueil).toContainText('Votre casier judiciaire est vierge');
    await expect(accueil).toContainText('Extrait de casier judiciaire (bulletin n°3)');
    await expect(accueil).toContainText('Certificat de nationalité burkinabè');

    const dossier = await inscrireParRedirection(page, 'entreprises', citoyen);

    expect(dossier.numero).toMatch(/^RC/);
    expect(dossier.etat).toBe('COMPLET');
    expect(dossier.verifications.map((v) => v.jeu).sort()).toEqual(JEUX);
    // État civil : les seuls champs que le registre du commerce inscrit.
    const identite = verification(dossier, 'etat-civil.identite');
    expect(identite.niveau).toBe('CHAMPS');
    expect(identite.champs).toEqual({
      nom: 'ZONGO',
      prenoms: 'Pingdwendé Rasmata',
      date_naissance: naissance,
    });
    // Nationalité et casier : oui ou non, rien d'autre.
    for (const jeu of ['nationalite.burkinabe', 'casier.neant']) {
      const statut = verification(dossier, jeu);
      expect(statut.niveau, jeu).toBe('STATUT');
      expect(statut.valeur, jeu).toBe(true);
      expect(statut.champs ?? {}, jeu).toEqual({});
      expect(statut.piece ?? null, jeu).toBeNull();
    }

    // L'extrait d'acte de naissance arrive en pièce PDF, avec la mention du registre.
    const extrait = verification(dossier, 'etat-civil.extrait-naissance');
    expect(extrait.niveau).toBe('DOCUMENT');
    expect(extrait.piece?.type).toBe('application/pdf');

    const principal = page.getByRole('main');
    await expect(principal.getByLabel('Nom', { exact: true })).toHaveValue('ZONGO');
    await deposer(page, 'entreprises');
    await expect(principal).toContainText('Votre dossier est complet');
    await expect(principal).toContainText('Les pièces que vous n’avez pas eu à fournir');
    const recu = (await reponsesDuService()).join('\n');
    expect(recu).not.toContain(citoyen.identifiant);
    expect(recu).not.toContain('lieu_naissance');
    expect(recu).not.toContain('Tribunal');

    // Le citoyen retrouve chaque consultation dans son espace.
    await page.goto(`${ADRESSES.citoyen}/historique`);
    const historique = page.getByRole('main');
    await expect(historique).toContainText(SERVICE);
    await expect(historique).toContainText('Casier judiciaire');
    await expect(historique).toContainText('Votre casier judiciaire est vierge');

    // L'agent du service instruit le dossier sans voir l'identifiant du citoyen.
    const contexteAgent = await browser.newContext();
    const agent = await contexteAgent.newPage();
    await connecterAgentService(agent, 'entreprises');
    const liste = await appeler<{ elements: { id: string; numero: string }[] }>(
      agent,
      '/api/agent/dossiers?taille=100',
    );
    const ligne = liste.corps.elements.find((d) => d.numero === dossier.numero);
    expect(ligne, 'l’agent retrouve le dossier').toBeDefined();
    const detail = await appeler(agent, `/api/agent/dossiers/${ligne?.id}`);
    expect(detail.statut).toBe(200);
    expect(detail.texte).toContain('ZONGO');
    expect(detail.texte).not.toContain(citoyen.identifiant);
    await contexteAgent.close();
  });
});
