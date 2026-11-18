import { ApplicationConfig } from '@angular/core';
import { fournirCoquille } from '@sira/coquille';
import { routes } from './app.routes';

export const configuration: ApplicationConfig = {
  providers: [fournirCoquille({ application: 'Espace citoyen Sira', routes })],
};
