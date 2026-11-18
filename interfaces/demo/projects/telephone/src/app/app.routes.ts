import { Routes } from '@angular/router';
import { ROUTES_ERREUR } from '@sira/coquille';

/** Le téléphone simulé n'a pas de connexion : il remplace un appareil, pas un compte. */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./messages/messages').then((m) => m.Messages),
  },
  ...ROUTES_ERREUR,
];
