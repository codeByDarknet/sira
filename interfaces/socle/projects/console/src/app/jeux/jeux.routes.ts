import { Routes } from '@angular/router';

/** Créer et modifier une question se fait avec l'assistant, dans une fenêtre. */
export const ROUTES_JEUX: Routes = [
  {
    path: '',
    title: 'Questions',
    loadComponent: () => import('./liste').then((m) => m.ListeJeux),
  },
  {
    path: ':code',
    title: 'Question',
    loadComponent: () => import('./detail').then((m) => m.DetailJeu),
  },
];
