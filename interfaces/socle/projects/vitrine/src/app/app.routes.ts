import { Routes } from '@angular/router';
import { ROUTES_ERREUR } from '@sira/coquille';

export const routes: Routes = [
  {
    path: '',
    title: 'Catalogue des composants',
    loadComponent: () => import('./catalogue/catalogue').then((m) => m.Catalogue),
  },
  {
    path: 'exemple',
    loadChildren: () => import('./exemple/exemple.routes').then((m) => m.ROUTES_EXEMPLE),
  },
  ...ROUTES_ERREUR,
];
