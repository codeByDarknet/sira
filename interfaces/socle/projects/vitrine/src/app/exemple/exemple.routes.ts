import { Routes } from '@angular/router';

/**
 * Routes d'un dossier fonctionnel, chargées à la demande. Dans une vraie application, la
 * route parente porte `canActivate: [gardeConnecte]`.
 */
export const ROUTES_EXEMPLE: Routes = [
  {
    path: '',
    title: 'Démarches',
    loadComponent: () => import('./liste').then((m) => m.Liste),
  },
  {
    path: 'nouvelle',
    title: 'Ajouter une démarche',
    loadComponent: () => import('./formulaire').then((m) => m.Formulaire),
  },
  {
    path: ':id',
    title: 'Détail d’une démarche',
    loadComponent: () => import('./detail').then((m) => m.Detail),
  },
];
