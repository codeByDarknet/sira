import { Routes } from '@angular/router';

/** Ajout et modification se font dans une fenêtre, depuis la liste ou la fiche. */
export const ROUTES_REGISTRES: Routes = [
  {
    path: '',
    title: 'Registres',
    loadComponent: () => import('./liste').then((m) => m.ListeRegistres),
  },
  {
    path: ':code',
    title: 'Registre',
    loadComponent: () => import('./detail').then((m) => m.DetailRegistre),
  },
];
