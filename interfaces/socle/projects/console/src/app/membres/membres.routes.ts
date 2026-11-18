import { Routes } from '@angular/router';

/** Ajout et modification se font dans une fenêtre, depuis la liste ou la fiche. */
export const ROUTES_MEMBRES: Routes = [
  {
    path: '',
    title: 'Membres',
    loadComponent: () => import('./liste').then((m) => m.ListeMembres),
  },
  {
    path: ':code',
    title: 'Membre',
    loadComponent: () => import('./detail').then((m) => m.DetailMembre),
  },
];
