import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Routes } from '@angular/router';
import { Session, fournirCoquille } from '@sira/coquille';

/*
 * Banc d'essai commun aux tests d'écran : la coquille réelle, un serveur simulé et une
 * session ouverte avec les rôles voulus.
 */

export const COMPTES = {
  instructeur: { nom: 'Issa SAWADOGO', roles: ['instructeur'] },
  autorite: { nom: 'Mariam KABORÉ', roles: ['autorite'] },
  auditeur: { nom: 'Paul ZONGO', roles: ['auditeur'] },
} as const;

export type Compte = keyof typeof COMPTES;

export async function ouvrirSession(compte: Compte, routes: Routes = []) {
  // jsdom n'ouvre pas les fenêtres modales : on n'en garde que l'état ouvert ou fermé.
  HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
    this.open = true;
  };
  HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
    this.open = false;
    this.dispatchEvent(new Event('close'));
  };
  TestBed.configureTestingModule({
    providers: [fournirCoquille({ application: 'Essai', routes }), provideHttpClientTesting()],
  });
  const serveur = TestBed.inject(HttpTestingController);
  const lecture = TestBed.inject(Session).charger();
  serveur.expectOne('/api/session').flush(COMPTES[compte]);
  await lecture;
  return serveur;
}

export function page<T>(elements: T[]) {
  return { elements, page: 1, taille: 20, total: elements.length };
}

export function textes(racine: HTMLElement, selecteur: string): string[] {
  return Array.from(racine.querySelectorAll(selecteur)).map((element) =>
    (element.textContent ?? '').replace(/\s+/g, ' ').trim(),
  );
}
