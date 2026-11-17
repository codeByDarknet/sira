import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { REDIRECTION, fournirCoquille } from '@sira/coquille';
import { IdentifiantSaisi } from '../parcours/identifiant';
import { Demarche } from '../parcours/parcours';
import { VotreDemande } from './demande';

const DEMARCHE: Demarche = {
  service: { code: 'concours', nom: 'Agence des concours', organisme: 'Ministère' },
  code: 'inscription',
  libelle: 'Inscription au concours',
  jeux: [
    {
      code: 'casier.vierge',
      libelle: 'Casier vierge',
      niveau: 'STATUT',
      registre: 'casier',
      champs: [],
    },
    {
      code: 'etat-civil.nom-prenoms',
      libelle: 'Nom et prénoms',
      niveau: 'CHAMPS',
      registre: 'etat-civil',
      champs: ['nom', 'prenoms'],
    },
    {
      code: 'etat-civil.extrait-naissance',
      libelle: 'Extrait d’acte de naissance',
      niveau: 'DOCUMENT',
      registre: 'etat-civil',
      champs: [],
    },
  ],
  declarations: [{ code: 'cv', libelle: 'Curriculum vitæ', aide: null, nature: 'PIECE' }],
  pieces: [],
};

describe('VotreDemande', () => {
  let serveur: HttpTestingController;
  let redirections: string[];

  beforeEach(() => {
    // jsdom n'ouvre pas les fenêtres modales : on n'en garde que l'état ouvert ou fermé.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
    redirections = [];
    TestBed.configureTestingModule({
      providers: [
        fournirCoquille({ application: 'Essai', routes: [] }),
        provideHttpClientTesting(),
        { provide: REDIRECTION, useValue: (url: string) => redirections.push(url) },
      ],
    });
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => serveur.verify());

  async function afficher() {
    const vue = TestBed.createComponent(VotreDemande);
    TestBed.tick();
    serveur.expectOne('/api/demarche').flush(DEMARCHE);
    await vue.whenStable();
    const page = vue.nativeElement as HTMLElement;
    const champ = page.querySelector('#identifiant-unique') as HTMLInputElement;
    const fenetre = page.querySelector('dialog') as HTMLDialogElement;
    const saisir = async (valeur: string) => {
      champ.value = valeur;
      champ.dispatchEvent(new Event('input'));
      (page.querySelector('form') as HTMLFormElement).dispatchEvent(
        new Event('submit', { cancelable: true }),
      );
      await vue.whenStable();
    };
    const bouton = (texte: string) =>
      Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.includes(texte));
    return { vue, page, champ, fenetre, saisir, bouton };
  }

  it('montre le formulaire verrouillé, chaque information avec son registre', async () => {
    const { page } = await afficher();
    const radios = Array.from(page.querySelectorAll<HTMLInputElement>('input[type=radio]'));
    expect(radios).toHaveLength(2);
    expect(radios.every((radio) => radio.disabled)).toBe(true);
    const champs = Array.from(page.querySelectorAll<HTMLInputElement>('input[type=text]'));
    expect(champs.filter((champ) => champ.disabled)).toHaveLength(2);
    expect(page.textContent).toContain('Fourni par Casier judiciaire');
    expect(page.querySelector('.sira-fichier')?.textContent).toContain('Fourni par');
  });

  it('refuse un identifiant mal formé sans rien envoyer ni ouvrir la fenêtre', async () => {
    const { page, fenetre, saisir } = await afficher();
    await saisir('12345');
    expect(page.textContent).toContain('Saisissez les 12 chiffres de votre identifiant unique.');
    expect(fenetre.open).toBe(false);
    expect(TestBed.inject(IdentifiantSaisi).identifiant()).toBeNull();
  });

  it('dit ce qui sera récupéré, puis ouvre la demande avec l’identifiant', async () => {
    const { vue, fenetre, saisir, bouton } = await afficher();
    await saisir('9900 4821 7350');
    expect(fenetre.open).toBe(true);
    expect(fenetre.textContent).toContain('Ce que nous allons récupérer');
    expect(fenetre.textContent).toContain('Autres façons de donner mon accord');

    bouton('Continuer avec Sira')?.click();
    const envoi = serveur.expectOne('/api/parcours/demandes');
    expect(envoi.request.method).toBe('POST');
    expect(envoi.request.body).toEqual({
      canal: 'REDIRECTION',
      identifiant_unique: '990048217350',
    });
    envoi.flush({ id: 'd1', canal: 'REDIRECTION', url_accord: 'https://sira.test/accord' });
    await vue.whenStable();
    expect(redirections).toEqual(['https://sira.test/accord']);
  });

  it('ramène au champ quand Sira refuse l’identifiant', async () => {
    const { vue, page, fenetre, saisir, bouton } = await afficher();
    await saisir('990048217350');
    bouton('Continuer avec Sira')?.click();
    serveur.expectOne('/api/parcours/demandes').flush(
      {
        type: 'urn:sira:erreur:validation',
        title: 'Saisie invalide',
        status: 400,
        erreurs: [{ champ: 'identifiant_unique', message: 'Identifiant inconnu.' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await vue.whenStable();
    expect(fenetre.open).toBe(false);
    expect(page.textContent).toContain('Identifiant inconnu.');
  });
});
