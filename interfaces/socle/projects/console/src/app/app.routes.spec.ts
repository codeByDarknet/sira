import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { routes } from './app.routes';
import { Compte, ouvrirSession } from './essais/banc';

async function aller(compte: Compte, adresse: string): Promise<string> {
  const serveur = await ouvrirSession(compte, routes);
  const routeur = TestBed.inject(Router);
  await routeur.navigateByUrl(adresse);
  // Les écrans atteints lancent leurs lectures : elles n'importent pas ici.
  serveur.match(() => true);
  return routeur.url;
}

/*
 * Ajouter et modifier se font dans des fenêtres, sur les listes et les fiches : il n'y a plus
 * d'adresse d'écriture à garder. Les boutons d'écriture ne s'affichent qu'à l'instructeur, et le
 * serveur reste seul juge de chaque appel.
 */
describe('routes de la console', () => {
  const LECTURES = [
    '/',
    '/membres',
    '/registres',
    '/questions',
    '/demarches',
    '/habilitations',
    '/journal',
    '/signalements',
  ];

  for (const compte of ['auditeur', 'autorite', 'instructeur'] as const) {
    it.each(LECTURES)(`ouvre la lecture de %s à l’${compte}`, async (adresse) => {
      expect(await aller(compte, adresse)).toBe(adresse);
    });
  }
});
