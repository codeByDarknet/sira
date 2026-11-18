import { Routes } from '@angular/router';
import { ROUTES_ERREUR, gardeConnecte } from '@sira/coquille';

/**
 * Le citoyen n'a pas de compte sur la plateforme : c'est Sira qui l'identifie. Seul l'espace
 * des agents demande une session.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./accueil/accueil').then((m) => m.Accueil),
  },
  {
    path: 'commencer',
    title: 'Votre demande',
    loadComponent: () => import('./demande/demande').then((m) => m.VotreDemande),
  },
  {
    path: 'accord/choix',
    title: 'Donner votre accord',
    loadComponent: () => import('./accord/choix').then((m) => m.Choix),
  },
  {
    path: 'accord',
    title: 'En attente de votre accord',
    loadComponent: () => import('./accord/attente').then((m) => m.Attente),
  },
  {
    path: 'retour',
    title: 'Retour de Sira',
    loadComponent: () => import('./accord/retour').then((m) => m.Retour),
  },
  {
    path: 'recuperation',
    title: 'Récupération de vos informations',
    loadComponent: () => import('./recuperation/recuperation').then((m) => m.Recuperation),
  },
  {
    path: 'dossier',
    title: 'Votre dossier',
    loadComponent: () => import('./dossier/dossier').then((m) => m.DossierCitoyen),
  },
  {
    path: 'agent',
    canActivate: [gardeConnecte],
    loadChildren: () => import('./agent/agent.routes').then((m) => m.ROUTES_AGENT),
  },
  ...ROUTES_ERREUR,
];
