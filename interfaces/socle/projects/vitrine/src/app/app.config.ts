import { ApplicationConfig } from '@angular/core';
import { fournirCoquille } from '@sira/coquille';
import { routes } from './app.routes';
import { apiSimulee } from './exemple/api-simulee';

export const configuration: ApplicationConfig = {
  providers: [
    // La vitrine n'a pas de serveur : `apiSimulee` répond dans le navigateur aux appels de
    // l'écran d'exemple. Une vraie application ne passe pas d'intercepteur.
    fournirCoquille({ application: 'Vitrine Sira', routes, intercepteurs: [apiSimulee] }),
  ],
};
