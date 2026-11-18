import { Routes } from '@angular/router';
import { ROUTES_ERREUR, gardeConnecte } from '@sira/coquille';

/**
 * Toute la console demande une session ; la lecture est ouverte aux trois rôles. Les routes
 * de création et de modification, dans chaque dossier, sont réservées à l'instructeur.
 */
export const routes: Routes = [
  {
    path: '',
    canActivate: [gardeConnecte],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Tableau de bord',
        loadComponent: () =>
          import('./tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'membres',
        loadChildren: () => import('./membres/membres.routes').then((m) => m.ROUTES_MEMBRES),
      },
      {
        path: 'registres',
        loadChildren: () => import('./registres/registres.routes').then((m) => m.ROUTES_REGISTRES),
      },
      {
        path: 'questions',
        loadChildren: () => import('./jeux/jeux.routes').then((m) => m.ROUTES_JEUX),
      },
      {
        path: 'demarches',
        loadChildren: () => import('./demarches/demarches.routes').then((m) => m.ROUTES_DEMARCHES),
      },
      {
        path: 'habilitations',
        loadChildren: () =>
          import('./habilitations/habilitations.routes').then((m) => m.ROUTES_HABILITATIONS),
      },
      {
        path: 'profil',
        title: 'Mon profil',
        loadComponent: () => import('@sira/coquille').then((m) => m.PageProfil),
        data: {
          actionsCompte: true,
          roles: {
            instructeur: 'Instructeur : prépare membres, registres, questions et habilitations',
            autorite: 'Autorité : approuve, rejette ou suspend les habilitations',
            auditeur: 'Auditeur : lit tout, ne modifie rien',
          },
        },
      },
      {
        path: 'journal',
        title: 'Journal',
        loadComponent: () => import('./journal/journal').then((m) => m.EcranJournal),
      },
      {
        path: 'signalements',
        title: 'Signalements',
        loadComponent: () => import('./signalements/signalements').then((m) => m.EcranSignalements),
      },
    ],
  },
  ...ROUTES_ERREUR,
];
