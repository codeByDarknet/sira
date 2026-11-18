import { Routes } from '@angular/router';
import { ROLES_GERER, ROLES_LIRE, UTILISATEURS_GERER, UTILISATEURS_LIRE } from '@demo/commun';
import { ROUTES_ERREUR, gardeConnecte, gardeDroit } from '@sira/coquille';
import {
  APPELS_LIRE,
  PANNES_GERER,
  PANNES_LIRE,
  PERSONNES_AJOUTER,
  PERSONNES_LIRE,
  PERSONNES_MODIFIER,
} from './droits';

/** Tout l'écran du registre demande une session d'agent. */
export const routes: Routes = [
  {
    path: '',
    canActivate: [gardeConnecte],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Tableau de bord',
        loadComponent: () => import('./accueil/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'personnes',
        canActivate: [gardeDroit(PERSONNES_LIRE)],
        title: 'Personnes',
        loadComponent: () => import('./personnes/liste').then((m) => m.Liste),
      },
      {
        path: 'personnes/nouvelle',
        canActivate: [gardeDroit(PERSONNES_AJOUTER)],
        title: 'Ajouter une personne',
        loadComponent: () => import('./personnes/correction').then((m) => m.Correction),
      },
      {
        path: 'personnes/:id',
        canActivate: [gardeDroit(PERSONNES_LIRE)],
        title: 'Fiche d’une personne',
        loadComponent: () => import('./personnes/fiche').then((m) => m.FichePersonne),
      },
      {
        path: 'personnes/:id/correction',
        canActivate: [gardeDroit(PERSONNES_MODIFIER)],
        title: 'Corriger une fiche',
        loadComponent: () => import('./personnes/correction').then((m) => m.Correction),
      },
      {
        path: 'pannes',
        canActivate: [gardeDroit(PANNES_LIRE, PANNES_GERER)],
        title: 'Pannes',
        loadComponent: () => import('./pannes/panneau').then((m) => m.Panneau),
      },
      {
        path: 'appels',
        canActivate: [gardeDroit(APPELS_LIRE)],
        title: 'Appels reçus',
        loadComponent: () => import('./appels/journal').then((m) => m.Journal),
      },
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
        path: 'profil',
        title: 'Mon profil',
        loadComponent: () => import('@sira/coquille').then((m) => m.PageProfil),
        data: { actionsCompte: true, roles: { agent: 'Agent du registre' } },
      },
    ],
  },
  ...ROUTES_ERREUR,
];
