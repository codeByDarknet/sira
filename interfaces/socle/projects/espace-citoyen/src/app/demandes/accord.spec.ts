import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { REDIRECTION, fournirCoquille } from '@sira/coquille';
import { Demande } from '../commun/modeles';
import { Accord } from './accord';
import { adresseDeRetour } from './presentation';

const ID = '3f2a6c1e-9b7d-4e21-8a55-0c1d2e3f4a5b';
const CHEMIN = `/api/demandes/${ID}`;
const DEMANDE: Demande = {
  id: ID,
  etat: 'EN_ATTENTE_ACCORD',
  canal: 'CODE',
  creee_le: '2026-11-16T09:00:00Z',
  expire_le: '2099-01-01T00:00:00Z',
  code_verification_exige: true,
  service: { code: 'concours', nom: 'Agence des concours' },
  demarche: {
    code: 'inscription',
    libelle: 'Inscription au concours',
    finalite: 'Vérifier les conditions du concours.',
    duree_autorisation_jours: 30,
  },
  jeux: [
    {
      code: 'majorite',
      libelle: 'Majorité',
      niveau: 'STATUT',
      registre: { code: 'etat-civil', nom: 'État civil' },
    },
  ],
};
const PROBLEME_404 = { status: 404, title: 'Introuvable', detail: 'Cette demande n’existe pas.' };

describe('Accord', () => {
  let serveur: HttpTestingController;
  let redirections: string[];

  beforeEach(() => {
    redirections = [];
    TestBed.configureTestingModule({
      providers: [
        fournirCoquille({ application: 'Essai', routes: [] }),
        provideHttpClientTesting(),
        { provide: REDIRECTION, useValue: (adresse: string) => redirections.push(adresse) },
      ],
    });
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => serveur.verify());

  async function ouvrir(demande: Demande = DEMANDE) {
    const vue = TestBed.createComponent(Accord);
    vue.componentRef.setInput('id', ID);
    // La lecture en cours retient `whenStable` : un tour de détection suffit à l'émettre.
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush(demande);
    await vue.whenStable();
    return { vue, page: vue.nativeElement as HTMLElement };
  }

  function bouton(page: HTMLElement, libelle: string): HTMLButtonElement {
    const trouve = Array.from(page.querySelectorAll('button')).find((element) =>
      element.textContent.includes(libelle),
    );
    if (!trouve) {
      throw new Error(`Bouton « ${libelle} » absent`);
    }
    return trouve;
  }

  function saisirCode(page: HTMLElement, code: string): void {
    const champ = page.querySelector<HTMLInputElement>('#code-service');
    if (!champ) {
      throw new Error('Champ du code absent');
    }
    champ.value = code;
    champ.dispatchEvent(new Event('input'));
  }

  it('montre qui demande, pour quelle démarche, demande le code du service, et deux boutons de même poids', async () => {
    const { page } = await ouvrir();

    expect(page.textContent).toContain('Agence des concours');
    expect(page.querySelector('.sira-chapeau')?.textContent?.trim()).toBe(
      'Pour : Inscription au concours',
    );
    expect(page.textContent).toContain('Vérifier les conditions du concours.');
    // Le code n'est jamais montré ici : seul l'écran du service le porte.
    expect(page.querySelector('.sira-code__chiffres')).toBeNull();
    expect(page.querySelector('#code-service')).not.toBeNull();
    expect(page.textContent).toContain('Vous n’êtes pas devant ce service ?');
    expect(bouton(page, 'Autoriser').className).toBe(bouton(page, 'Refuser').className);
  });

  it('ne demande pas de code quand on vient de l’écran du service', async () => {
    const { page } = await ouvrir({
      ...DEMANDE,
      canal: 'REDIRECTION',
      code_verification_exige: false,
    });
    expect(page.querySelector('#code-service')).toBeNull();
  });

  it('n’envoie pas un accord sans le code du service', async () => {
    const { vue, page } = await ouvrir();

    saisirCode(page, '49');
    bouton(page, 'Autoriser').click();
    await vue.whenStable();

    serveur.expectNone(`${CHEMIN}/decision`);
    expect(page.textContent).toContain('Saisissez les 6 chiffres affichés par le service');
  });

  it('envoie l’accord avec le code recopié, puis ramène au service', async () => {
    const { vue, page } = await ouvrir();

    saisirCode(page, '492 113');
    bouton(page, 'Autoriser').click();
    const envoi = serveur.expectOne(`${CHEMIN}/decision`);
    expect(envoi.request.method).toBe('POST');
    expect(envoi.request.body).toEqual({ decision: 'ACCEPTER', code_verification: '492113' });
    envoi.flush({ etat: 'EN_COURS', retour_url: 'https://concours.test/suite?etat=abc' });
    await vue.whenStable();

    expect(redirections).toEqual(['https://concours.test/suite?etat=abc']);
  });

  it('dit qu’un code faux est refusé et laisse réessayer', async () => {
    const { vue, page } = await ouvrir();

    saisirCode(page, '000000');
    bouton(page, 'Autoriser').click();
    serveur
      .expectOne(`${CHEMIN}/decision`)
      .flush(
        { status: 422, title: 'Code invalide', detail: 'Ce code ne correspond pas.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush(DEMANDE);
    await vue.whenStable();

    expect(page.textContent).toContain('Votre réponse n’a pas été enregistrée');
    expect(page.querySelector<HTMLInputElement>('#code-service')?.value).toBe('');
    expect(bouton(page, 'Autoriser')).toBeTruthy();
  });

  it('envoie le refus et le confirme sur place quand il n’y a pas de retour', async () => {
    const { vue, page } = await ouvrir();

    bouton(page, 'Refuser').click();
    const envoi = serveur.expectOne(`${CHEMIN}/decision`);
    expect(envoi.request.body).toEqual({ decision: 'REFUSER' });
    envoi.flush({ etat: 'REFUSEE', retour_url: null });
    await vue.whenStable();

    expect(redirections).toEqual([]);
    expect(page.querySelector('h1')?.textContent).toContain('Vous avez refusé la demande');
    expect(page.textContent).toContain('Agence des concours n’a rien reçu');
    expect(page.querySelector('button')).toBeNull();
  });

  it('ne suit pas une adresse de retour qui n’est pas une adresse web', async () => {
    const { vue, page } = await ouvrir();
    saisirCode(page, '492113');

    bouton(page, 'Autoriser').click();
    serveur
      .expectOne(`${CHEMIN}/decision`)
      .flush({ etat: 'EN_COURS', retour_url: 'javascript:alert(1)' });
    await vue.whenStable();

    expect(redirections).toEqual([]);
    expect(page.querySelector('h1')?.textContent).toContain('Vous avez autorisé l’accès');
  });

  it('rattache une demande encore à personne, arrivée par QR code', async () => {
    const vue = TestBed.createComponent(Accord);
    vue.componentRef.setInput('id', ID);
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush(PROBLEME_404, { status: 404, statusText: 'Not Found' });
    const rattachement = serveur.expectOne(`${CHEMIN}/rattachement`);
    expect(rattachement.request.method).toBe('POST');
    rattachement.flush(DEMANDE);
    await vue.whenStable();

    expect(vue.nativeElement.textContent).toContain('Agence des concours');
  });

  it('dit qu’une demande d’autrui ou inconnue est introuvable, sans bouton', async () => {
    const vue = TestBed.createComponent(Accord);
    vue.componentRef.setInput('id', ID);
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush(PROBLEME_404, { status: 404, statusText: 'Not Found' });
    serveur
      .expectOne(`${CHEMIN}/rattachement`)
      .flush(PROBLEME_404, { status: 404, statusText: 'Not Found' });
    await vue.whenStable();
    const page = vue.nativeElement as HTMLElement;

    expect(page.querySelector('h1')?.textContent).toContain('introuvable');
    expect(page.querySelector('button')).toBeNull();
  });

  it.each([
    ['REFUSEE', 'Vous avez refusé cette demande'],
    ['EXPIREE', 'Le délai pour répondre est dépassé'],
    ['COMPLETE', 'Vous avez déjà autorisé cette demande'],
  ] as const)('ne propose plus de décision pour une demande %s', async (etat, raison) => {
    const { page } = await ouvrir({ ...DEMANDE, etat });

    expect(page.querySelector('h1')?.textContent).toContain('n’est plus valable');
    expect(page.textContent).toContain(raison);
    expect(page.querySelector('button')).toBeNull();
  });

  it('garde la demande et dit l’échec quand le serveur refuse la réponse (rejeu)', async () => {
    const { vue, page } = await ouvrir();
    saisirCode(page, '492113');

    bouton(page, 'Autoriser').click();
    serveur
      .expectOne(`${CHEMIN}/decision`)
      .flush(
        { status: 409, title: 'Conflit', detail: 'Cette demande a déjà reçu une réponse.' },
        { status: 409, statusText: 'Conflict' },
      );
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush({ ...DEMANDE, etat: 'REFUSEE' });
    await vue.whenStable();

    expect(redirections).toEqual([]);
    expect(page.textContent).toContain('Votre réponse n’a pas été enregistrée');
    expect(page.querySelector('button')).toBeNull();
  });

  it('confirme l’accord quand la réponse se perd mais que la décision est enregistrée', async () => {
    const { vue, page } = await ouvrir();
    saisirCode(page, '492113');

    bouton(page, 'Autoriser').click();
    serveur
      .expectOne(`${CHEMIN}/decision`)
      .flush(
        { status: 503, title: 'Indisponible', detail: 'Service momentanément indisponible' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    serveur.expectOne(CHEMIN).flush({ ...DEMANDE, etat: 'EN_COURS' });
    await vue.whenStable();

    expect(page.querySelector('h1')?.textContent).toContain('Vous avez autorisé l’accès');
    expect(page.textContent).toContain('votre accord est bien enregistré');
    expect(page.textContent).not.toContain('Votre réponse n’a pas été enregistrée');
  });

  it('dit l’échec quand la réponse se perd et que rien n’est enregistré', async () => {
    const { vue, page } = await ouvrir();
    saisirCode(page, '492113');

    bouton(page, 'Autoriser').click();
    serveur
      .expectOne(`${CHEMIN}/decision`)
      .flush(
        { status: 503, title: 'Indisponible', detail: 'Service momentanément indisponible' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    serveur.expectOne(CHEMIN).flush(DEMANDE);
    TestBed.tick();
    serveur.expectOne(CHEMIN).flush(DEMANDE);
    await vue.whenStable();

    expect(page.textContent).toContain('Votre réponse n’a pas été enregistrée');
    expect(page.querySelector('h1')?.textContent).toContain('Un service demande vos informations');
  });
});

describe('adresseDeRetour', () => {
  it('n’admet que les adresses web', () => {
    expect(adresseDeRetour('https://concours.test/suite')).toBe('https://concours.test/suite');
    expect(adresseDeRetour('javascript:alert(1)')).toBeNull();
    expect(adresseDeRetour('data:text/html,x')).toBeNull();
    expect(adresseDeRetour('/chemin/relatif')).toBeNull();
    expect(adresseDeRetour(null)).toBeNull();
  });
});
