import { Routes } from '@angular/router';
import { ROUTES_ERREUR, gardeConnecte } from '@sira/coquille';
import { gardeIdentifiant } from './commun/identifiant';

/**
 * Un dossier par fonction, chargé à la demande. L'accueil et l'aide sont publics ; tout le
 * reste demande une session, que le serveur revérifie à chaque appel.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./accueil/accueil').then((m) => m.Accueil),
  },
  {
    path: 'aide',
    title: 'Comment ça marche',
    loadComponent: () => import('./aide/fonctionnement').then((m) => m.Fonctionnement),
  },
  {
    path: 'aide/vos-droits',
    title: 'Vos droits',
    loadComponent: () => import('./aide/droits').then((m) => m.Droits),
  },
  {
    path: 'mon-espace',
    title: 'Mon espace',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
  },
  {
    path: 'scanner',
    title: 'Scanner un code',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./scanner/scanner').then((m) => m.Scanner),
  },
  {
    path: 'accord/:id',
    title: 'Demande d’accès',
    canMatch: [gardeIdentifiant],
    canActivate: [gardeConnecte],
    loadComponent: () => import('./demandes/accord').then((m) => m.Accord),
  },
  {
    path: 'demandes',
    title: 'Demandes en attente',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./demandes/en-attente').then((m) => m.EnAttente),
  },
  {
    path: 'acces',
    title: 'Mes accès',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./acces/liste').then((m) => m.ListeAcces),
  },
  {
    path: 'acces/:id',
    title: 'Détail d’un accès',
    canMatch: [gardeIdentifiant],
    canActivate: [gardeConnecte],
    loadComponent: () => import('./acces/detail').then((m) => m.DetailAcces),
  },
  {
    path: 'historique',
    title: 'Qui a consulté mes informations',
    canActivate: [gardeConnecte],
    loadComponent: () => import('./historique/historique').then((m) => m.Historique),
  },
  {
    path: 'profil',
    title: 'Mon profil',
    canActivate: [gardeConnecte],
    loadComponent: () => import('@sira/coquille').then((m) => m.PageProfil),
  },
  ...ROUTES_ERREUR,
];
