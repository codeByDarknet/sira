import { Routes } from '@angular/router';

/** Ajout et modification se font dans une fenêtre, depuis la liste ou la fiche. */
export const ROUTES_DEMARCHES: Routes = [
  {
    path: '',
    title: 'Démarches',
    loadComponent: () => import('./liste').then((m) => m.ListeDemarches),
  },
  {
    path: ':code',
    title: 'Démarche',
    loadComponent: () => import('./detail').then((m) => m.DetailDemarche),
  },
];
