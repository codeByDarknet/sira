import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { REDIRECTION } from '../api/redirection';
import { fournirCoquille } from '../fournir';
import { gardeConnecte, gardeRole } from './gardes';
import { Session } from './session';

describe('gardeConnecte', () => {
  let serveur: HttpTestingController;
  let redirections: string[];

  const route = {} as ActivatedRouteSnapshot;
  const etat = { url: '/demandes/7' } as RouterStateSnapshot;
  const garder = () => TestBed.runInInjectionContext(() => gardeConnecte(route, etat));

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

  it('ouvre la route à un usager connecté et retient qui il est', async () => {
    const decision = garder();
    serveur.expectOne('/api/session').flush({ nom: 'Aminata OUÉDRAOGO', roles: ['citoyen'] });

    expect(await decision).toBe(true);
    expect(TestBed.inject(Session).utilisateur()?.nom).toBe('Aminata OUÉDRAOGO');
    expect(TestBed.runInInjectionContext(() => gardeRole('citoyen')(route, etat))).toBe(true);
    expect(
      TestBed.runInInjectionContext(() => gardeRole('administrateur')(route, etat)),
    ).toBeInstanceOf(UrlTree);
  });

  it('envoie se connecter un usager sans session, avec l’adresse demandée en retour', async () => {
    const decision = garder();
    serveur.expectOne('/api/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await decision).toBe(false);
    expect(redirections).toEqual(['/api/auth/connexion/sira?retour=%2Fdemandes%2F7']);
  });

  it('affiche l’écran d’indisponibilité quand le serveur ne répond pas', async () => {
    const decision = garder();
    // Une lecture est réessayée deux fois avant d'abandonner.
    for (let essai = 0; essai <= 2; essai++) {
      await vi
        .waitFor(() => serveur.expectOne('/api/session'), { timeout: 3000 })
        .then((requete) => requete.flush(null, { status: 503, statusText: 'Service Unavailable' }));
    }

    expect(String(await decision)).toBe('/indisponible');
  });
});
