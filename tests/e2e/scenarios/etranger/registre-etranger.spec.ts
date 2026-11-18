import { Page, expect, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';
import { CITOYENS } from '../../outils/comptes';
import { connecterAdministrateur, connecterCitoyen } from '../../outils/connexion';
import { appeler } from '../../outils/navigateur';
import {
  attendreDossier,
  attendreEcranAccord,
  ouvrirDemande,
  repondre,
  verification,
} from '../../outils/service';

/**
 * Preuve de raccordement : le « registre d'une autre équipe » (registres/etranger, profil compose
 * « etranger ») ne parle pas le contrat commun. Seul un fichier de raccordement le décrit au
 * connecteur. Lancement : scripts/sira etranger (docs/brancher-un-registre.md).
 *
 * Le service de concours ne connaît qu'une démarche : son habilitation est suspendue le temps de
 * la preuve, remplacée par une habilitation qui ne demande que la question nouvelle, puis rétablie.
 */

interface Habilitation {
  id: string;
  service: string;
  demarche: string;
  jeux: string[];
  etat: string;
}
interface PageDe<T> {
  elements: T[];
}
interface LigneJournal {
  type: string;
  resultat: string;
  registre: { code: string } | null;
  jeu: { code: string } | null;
}

const REGISTRE = 'etranger';
const DEMARCHE = 'concours-direct';

async function ecrire(page: Page, chemin: string, methode: 'POST' | 'PUT', corps?: unknown) {
  const reponse = await appeler(page, chemin, methode, corps);
  expect(reponse.statut, `${methode} ${chemin} : ${reponse.texte}`).toBeLessThan(300);
  return reponse;
}

/** Déclare la ressource si elle n'existe pas encore : la preuve se rejoue. */
async function declarer(page: Page, ressource: string, code: string, corps: unknown) {
  if ((await appeler(page, `/api/${ressource}/${code}`)).statut === 404) {
    await ecrire(page, `/api/${ressource}`, 'POST', corps);
  }
}

async function habilitationsConcours(page: Page, etat: string): Promise<Habilitation[]> {
  const liste = await appeler<PageDe<Habilitation>>(
    page,
    `/api/habilitations?etat=${etat}&taille=100`,
  );
  expect(liste.statut).toBe(200);
  return liste.corps.elements.filter((h) => h.service === 'concours' && h.demarche === DEMARCHE);
}

test.describe('Raccordement du registre d’une autre équipe', () => {
  test.setTimeout(420_000);

  test('déclaré, questionné, habilité : « majeur : oui » de bout en bout, consultation au journal', async ({
    page,
    browser,
  }) => {
    const suffixe = Date.now().toString(36);
    const jeu = `${REGISTRE}.majeur-${suffixe}`;

    // 1. L'instructeur déclare le membre et son registre (adresse du connecteur posé devant lui).
    await connecterAdministrateur(page, 'instructeur', '/');
    await declarer(page, 'membres', REGISTRE, {
      code: REGISTRE,
      nom: 'Registre d’une autre équipe',
      organisme: 'Équipe voisine (démonstration)',
      roles: ['REGISTRE'],
      identite: `urn:sira:registre:${REGISTRE}`,
    });
    await declarer(page, 'registres', REGISTRE, {
      code: REGISTRE,
      nom: 'Registre d’une autre équipe',
      membre: REGISTRE,
      adresse: `https://connecteur-${REGISTRE}:8443`,
      transport: 'DIRECT',
    });

    // 2. La question : essayée sur une personne fictive, puis publiée.
    const question = {
      code: jeu,
      registre: REGISTRE,
      niveau: 'STATUT',
      libelle: `Vous êtes majeur (registre d’une autre équipe, ${suffixe})`,
      description: 'Répond oui ou non. La date de naissance n’est pas transmise.',
      validite: 'P90D',
      regle: { champ: 'date_naissance', operateur: 'AGE_AU_MOINS', valeur: 18 },
    };
    const essai = await ecrire(page, '/api/jeux/essai', 'POST', {
      jeu: question,
      identifiant_unique: CITOYENS.aicha.identifiant,
    });
    expect(essai.texte).toContain('true');
    expect(essai.texte, 'la date de naissance ne sort pas').not.toContain('2003-03-14');
    await ecrire(page, '/api/jeux', 'POST', question);

    // 3. Habilitation : préparée par l'instructeur, approuvée par l'autorité.
    const contexteAutorite = await browser.newContext();
    const autorite = await contexteAutorite.newPage();
    await connecterAdministrateur(autorite, 'autorite', '/');
    const [origine] = await habilitationsConcours(autorite, 'APPROUVEE');
    expect(origine, 'une habilitation du concours est en vigueur').toBeDefined();
    let nouvelle: Habilitation | undefined;
    try {
      await ecrire(autorite, `/api/habilitations/${origine.id}/suspension`, 'POST', {
        motif: 'Preuve de raccordement d’un registre : rétablie à la fin.',
      });
      nouvelle = (
        await ecrire(page, '/api/habilitations', 'POST', {
          service: 'concours',
          demarche: DEMARCHE,
          jeux: [jeu],
          justification: 'Preuve : la majorité vérifiée par le registre d’une autre équipe.',
        })
      ).corps as Habilitation;
      await ecrire(page, `/api/habilitations/${nouvelle.id}/soumission`, 'POST');
      const approuvee = await ecrire(
        autorite,
        `/api/habilitations/${nouvelle.id}/approbation`,
        'POST',
      );
      expect((approuvee.corps as Habilitation).etat).toBe('APPROUVEE');

      // 4. Un citoyen s'inscrit au concours, autorise, et le service reçoit « oui ».
      const contexteCitoyen = await browser.newContext();
      const citoyen = await contexteCitoyen.newPage();
      await connecterCitoyen(citoyen, CITOYENS.hamidou.identifiant);
      await ouvrirDemande(citoyen, 'concours', 'REDIRECTION', CITOYENS.hamidou.identifiant);
      await attendreEcranAccord(citoyen);
      await expect(citoyen.getByRole('main')).toContainText('registre d’une autre équipe');
      await repondre(citoyen, 'Autoriser');
      const dossier = await attendreDossier(citoyen, 'concours');
      const recue = verification(dossier, jeu);
      expect(recue.etat).toBe('OBTENU');
      expect(recue.valeur, 'majeur : oui').toBe(true);
      expect(recue.champs, 'aucune donnée brute').toBeNull();
      await expect(citoyen.getByRole('main')).toContainText(question.libelle);
      await contexteCitoyen.close();

      // 5. Le journal du socle montre la consultation, et reste intact.
      const journal = await appeler<PageDe<LigneJournal>>(
        autorite,
        `/api/journal?type=CONSULTATION&registre=${REGISTRE}&taille=50`,
      );
      expect(journal.statut).toBe(200);
      expect(
        journal.corps.elements.some((l) => l.jeu?.code === jeu && l.registre?.code === REGISTRE),
        'consultation du registre étranger au journal',
      ).toBe(true);
      await autorite.goto(`${ADRESSES.console}/journal`);
      await autorite.getByRole('button', { name: 'Vérifier l’intégrité' }).click();
      await expect(autorite.getByText('Le journal est intact')).toBeVisible({ timeout: 60_000 });
    } finally {
      // La démonstration ordinaire reprend : habilitation de la preuve suspendue, l'autre rétablie.
      if (
        nouvelle &&
        (await appeler<Habilitation>(autorite, `/api/habilitations/${nouvelle.id}`)).corps.etat ===
          'APPROUVEE'
      ) {
        await ecrire(autorite, `/api/habilitations/${nouvelle.id}/suspension`, 'POST', {
          motif: 'Fin de la preuve de raccordement.',
        });
      }
      if (
        (await appeler<Habilitation>(autorite, `/api/habilitations/${origine.id}`)).corps.etat ===
        'SUSPENDUE'
      ) {
        await ecrire(autorite, `/api/habilitations/${origine.id}/approbation`, 'POST');
      }
      await contexteAutorite.close();
    }
  });
});
