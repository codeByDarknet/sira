import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { fournirCoquille } from '@sira/coquille';
import { Autorisation } from '../commun/modeles';
import { Retrait } from './retrait';

const ID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
const AUTORISATION: Autorisation = {
  id: ID,
  etat: 'ACTIVE',
  accordee_le: '2026-11-01T09:00:00Z',
  expire_le: '2026-12-01T09:00:00Z',
  retiree_le: null,
  service: { code: 'concours', nom: 'Agence des concours' },
  demarche: { code: 'inscription', libelle: 'Inscription au concours', finalite: 'Vérifier.' },
  jeux: [],
  consultations: 2,
  derniere_consultation_le: '2026-11-02T10:00:00Z',
};

describe('Retrait', () => {
  let serveur: HttpTestingController;

  beforeEach(() => {
    // jsdom n'ouvre pas les fenêtres modales : on n'en garde que l'état ouvert ou fermé.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
    TestBed.configureTestingModule({
      providers: [
        fournirCoquille({ application: 'Essai', routes: [] }),
        provideHttpClientTesting(),
      ],
    });
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => serveur.verify());

  async function afficher() {
    const vue = TestBed.createComponent(Retrait);
    vue.componentRef.setInput('autorisation', AUTORISATION);
    const retires: Autorisation[] = [];
    vue.componentInstance.retire.subscribe((autorisation) => retires.push(autorisation));
    await vue.whenStable();
    const page = vue.nativeElement as HTMLElement;
    const fenetre = page.querySelector('dialog') as HTMLDialogElement;
    const boutons = () => Array.from(page.querySelectorAll('button'));
    return { vue, page, fenetre, boutons, retires };
  }

  it('ne retire rien au premier clic : il ouvre la fenêtre, qui dit ce qui reste', async () => {
    const { vue, fenetre, boutons } = await afficher();
    expect(fenetre.open).toBe(false);

    boutons()[0].click();
    await vue.whenStable();

    expect(fenetre.open).toBe(true);
    expect(fenetre.textContent).toContain('Retirer l’accès de Agence des concours ?');
    expect(fenetre.textContent).toContain('Ce qui a déjà été transmis reste chez ce service.');
    serveur.expectNone(`/api/autorisations/${ID}`);
  });

  it('retire l’accès à la confirmation, puis prévient l’écran', async () => {
    const { vue, fenetre, boutons, retires } = await afficher();
    boutons()[0].click();
    await vue.whenStable();

    boutons()[1].click();
    const envoi = serveur.expectOne(`/api/autorisations/${ID}`);
    expect(envoi.request.method).toBe('DELETE');
    envoi.flush(null, { status: 204, statusText: 'No Content' });
    await vue.whenStable();

    expect(retires).toEqual([AUTORISATION]);
    expect(fenetre.open).toBe(false);
  });

  it('referme sans rien envoyer sur « Annuler »', async () => {
    const { vue, fenetre, boutons, retires } = await afficher();
    boutons()[0].click();
    await vue.whenStable();

    boutons()[2].click();
    await vue.whenStable();

    expect(fenetre.open).toBe(false);
    expect(retires).toEqual([]);
  });

  it('dit l’échec dans la fenêtre et ne prétend pas avoir retiré l’accès', async () => {
    const { vue, fenetre, boutons, retires } = await afficher();
    boutons()[0].click();
    await vue.whenStable();

    boutons()[1].click();
    serveur
      .expectOne(`/api/autorisations/${ID}`)
      .flush(
        { status: 404, title: 'Introuvable', detail: 'Cet accès n’existe pas.' },
        { status: 404, statusText: 'Not Found' },
      );
    await vue.whenStable();

    expect(retires).toEqual([]);
    expect(fenetre.open).toBe(true);
    expect(fenetre.textContent).toContain('L’accès n’a pas été retiré');
  });
});
