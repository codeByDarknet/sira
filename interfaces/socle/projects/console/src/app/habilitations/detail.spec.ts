import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Compte, ouvrirSession, page } from '../essais/banc';
import { DetailHabilitation } from './detail';
import { EtatHabilitation, Habilitation } from './habilitations';

const ID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';

function habilitation(etat: EtatHabilitation, instruitePar = 'Issa SAWADOGO'): Habilitation {
  return {
    id: ID,
    service: 'concours',
    demarche: 'concours-direct',
    jeux: ['etat-civil.majeur'],
    etat,
    justification: 'Le concours est réservé aux personnes majeures.',
    instruite_par: instruitePar,
    decidee_par: null,
    decidee_le: null,
  };
}

describe('DetailHabilitation', () => {
  let serveur: HttpTestingController;

  afterEach(() => serveur.verify());

  async function afficher(compte: Compte, fiche: Habilitation) {
    serveur = await ouvrirSession(compte);
    const vue = TestBed.createComponent(DetailHabilitation);
    vue.componentRef.setInput('id', ID);
    // Tant qu'une lecture est en cours l'écran n'est pas stable : on avance d'un rendu.
    TestBed.tick();
    serveur.expectOne(`/api/habilitations/${ID}`).flush(fiche);
    TestBed.tick();
    // Les listes de noms (membres, registres, démarches, questions) sont lues en même temps.
    for (const url of ['/api/jeux', '/api/membres', '/api/registres', '/api/demarches']) {
      serveur.match((requete) => requete.url === url).forEach((r) => r.flush(page([])));
    }
    await vue.whenStable();
    const ecran = vue.nativeElement as HTMLElement;
    // La fenêtre de confirmation, fermée, a ses propres boutons : on ne compte que ceux de l'écran.
    const gestes = () =>
      Array.from(ecran.querySelectorAll('.sira-fiche__actions > *'))
        .filter((element) => !element.closest('dialog'))
        .map((element) => element.textContent?.trim());
    const bouton = (libelle: string) =>
      Array.from(ecran.querySelectorAll('button')).find(
        (element) => element.textContent?.trim() === libelle,
      ) as HTMLButtonElement;
    return { vue, ecran, gestes, bouton };
  }

  it('propose à l’instructeur de soumettre et de modifier son brouillon', async () => {
    const { gestes } = await afficher('instructeur', habilitation('BROUILLON'));
    expect(gestes()).toEqual(['Modifier', 'Soumettre à l’autorité']);
  });

  it('ne montre à l’instructeur aucun bouton de décision sur un dossier soumis', async () => {
    const { gestes } = await afficher('instructeur', habilitation('SOUMISE'));
    expect(gestes()).toEqual([]);
  });

  it('propose à l’autorité d’approuver ou de rejeter, pas de modifier', async () => {
    const { gestes } = await afficher('autorite', habilitation('SOUMISE'));
    expect(gestes()).toEqual(['Approuver', 'Rejeter']);
  });

  it('ne montre aucun geste à l’auditeur', async () => {
    const { gestes } = await afficher('auditeur', habilitation('SOUMISE'));
    expect(gestes()).toEqual([]);
  });

  it('cache la décision à l’autorité qui a préparé le dossier', async () => {
    const { gestes } = await afficher('autorite', habilitation('SOUMISE', 'Mariam KABORÉ'));
    expect(gestes()).toEqual([]);
  });

  it('n’envoie pas un rejet sans motif', async () => {
    const { vue, bouton, ecran } = await afficher('autorite', habilitation('SOUMISE'));
    bouton('Rejeter').click();
    await vue.whenStable();
    const confirmer = ecran.querySelector('dialog [dialogue-actions]') as HTMLButtonElement;
    confirmer.click();
    await vue.whenStable();

    serveur.expectNone(`/api/habilitations/${ID}/rejet`);
    expect(ecran.querySelector('dialog')?.textContent).toContain('Expliquez votre décision');
  });

  it('affiche le refus du serveur quand la séparation des rôles s’applique', async () => {
    const { vue, bouton, ecran } = await afficher('autorite', habilitation('SOUMISE'));
    bouton('Approuver').click();
    await vue.whenStable();
    (ecran.querySelector('dialog [dialogue-actions]') as HTMLButtonElement).click();
    serveur.expectOne(`/api/habilitations/${ID}/approbation`).flush(
      {
        status: 403,
        code: 'SEPARATION_DES_ROLES',
        detail: 'Vous ne pouvez pas décider d’une habilitation que vous avez instruite.',
      },
      { status: 403, statusText: 'Forbidden' },
    );
    await vue.whenStable();

    expect(ecran.querySelector('dialog')?.textContent).toContain(
      'Vous ne pouvez pas décider d’une habilitation que vous avez instruite.',
    );
    expect(ecran.textContent).not.toContain('Habilitation approuvée.');
  });
});
