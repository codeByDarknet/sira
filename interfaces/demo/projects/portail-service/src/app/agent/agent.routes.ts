import { Routes } from '@angular/router';
import { ROLES_GERER, ROLES_LIRE, UTILISATEURS_GERER, UTILISATEURS_LIRE } from '@demo/commun';
import { gardeDroit } from '@sira/coquille';

/** Écrans des agents du service. */
export const ROUTES_AGENT: Routes = [
  {
    path: 'utilisateurs',
    title: 'Utilisateurs',
    canActivate: [gardeDroit(UTILISATEURS_LIRE, UTILISATEURS_GERER)],
    loadComponent: () => import('@demo/agents').then((m) => m.PageUtilisateurs),
  },
  {
    path: 'roles',
    title: 'Rôles',
    canActivate: [gardeDroit(ROLES_LIRE, ROLES_GERER)],
    loadComponent: () => import('@demo/agents').then((m) => m.PageRoles),
  },
  {
    path: '',
    canActivate: [gardeDroit('DOSSIERS_LIRE')],
    title: 'Dossiers reçus',
    loadComponent: () => import('./liste').then((m) => m.Liste),
  },
  {
    path: ':id',
    canActivate: [gardeDroit('DOSSIERS_LIRE')],
    title: 'Dossier',
    loadComponent: () => import('./detail').then((m) => m.Detail),
  },
];
