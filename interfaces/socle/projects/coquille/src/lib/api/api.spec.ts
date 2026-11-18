import { HttpContext } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { fournirCoquille } from '../fournir';
import { Api } from './api';
import { SANS_REDIRECTION } from './intercepteur';
import { lireProbleme } from './probleme';
import { CHEMIN_COURANT, REDIRECTION } from './redirection';

describe('Api', () => {
  let api: Api;
  let serveur: HttpTestingController;
  let redirections: string[];

  beforeEach(() => {
    redirections = [];
    TestBed.configureTestingModule({
      providers: [
        fournirCoquille({ application: 'Essai', routes: [] }),
        provideHttpClientTesting(),
        { provide: REDIRECTION, useValue: (adresse: string) => redirections.push(adresse) },
        { provide: CHEMIN_COURANT, useValue: () => '/acces/42?onglet=historique' },
      ],
    });
    api = TestBed.inject(Api);
    serveur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    serveur.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('renvoie le jeton CSRF du cookie dans l’en-tête des appels qui modifient', () => {
    document.cookie = 'XSRF-TOKEN=jeton-d-essai; path=/';

    api.creer('/api/accords', { reponse: 'AUTORISE' }).subscribe();
    const ecriture = serveur.expectOne('/api/accords');
    expect(ecriture.request.headers.get('X-XSRF-TOKEN')).toBe('jeton-d-essai');
    ecriture.flush({});

    api.lire('/api/accords').subscribe();
    const lecture = serveur.expectOne('/api/accords');
    expect(lecture.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    lecture.flush({});
  });

  it('modifie partiellement avec PATCH, jeton CSRF compris', () => {
    document.cookie = 'XSRF-TOKEN=jeton-d-essai; path=/';

    api.modifier('/api/personnes/42', { champs: { nom: 'OUEDRAOGO' } }).subscribe();
    const modification = serveur.expectOne('/api/personnes/42');
    expect(modification.request.method).toBe('PATCH');
    expect(modification.request.body).toEqual({ champs: { nom: 'OUEDRAOGO' } });
    expect(modification.request.headers.get('X-XSRF-TOKEN')).toBe('jeton-d-essai');
    modification.flush({});
  });

  it('n’envoie pas les valeurs de recherche absentes', () => {
    api.lire('/api/accords', { page: 2, recherche: '', etat: undefined }).subscribe();
    serveur.expectOne('/api/accords?page=2').flush({});
  });

  it('envoie l’usager se connecter quand la session a expiré, puis le ramène sur son écran', () => {
    let issue = 'en attente';
    api.lire('/api/accords').subscribe({
      next: () => (issue = 'valeur'),
      error: () => (issue = 'erreur'),
    });
    serveur.expectOne('/api/accords').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(redirections).toEqual([
      '/api/auth/connexion/sira?retour=%2Facces%2F42%3Fonglet%3Dhistorique',
    ]);
    expect(issue).toBe('en attente');
  });

  it('laisse l’appelant traiter le 401 quand il le demande', () => {
    let statut = 0;
    const contexte = new HttpContext().set(SANS_REDIRECTION, true);
    api.lire('/api/session', {}, contexte).subscribe({
      error: (erreur: unknown) => (statut = lireProbleme(erreur).statut),
    });
    serveur.expectOne('/api/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(redirections).toEqual([]);
    expect(statut).toBe(401);
  });
});
