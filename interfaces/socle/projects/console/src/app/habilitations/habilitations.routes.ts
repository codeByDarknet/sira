import { Routes } from '@angular/router';

/** Préparer et modifier un brouillon se fait dans une fenêtre, depuis la liste ou la fiche. */
export const ROUTES_HABILITATIONS: Routes = [
  {
    path: '',
    title: 'Habilitations',
    loadComponent: () => import('./liste').then((m) => m.ListeHabilitations),
  },
  {
    path: ':id',
    title: 'Habilitation',
    loadComponent: () => import('./detail').then((m) => m.DetailHabilitation),
  },
];
