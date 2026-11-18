import { demarrer } from '@demo/commun';
import { App } from './app/app';
import { routes } from './app/app.routes';

void demarrer(App, routes, {
  nom: 'Téléphone',
  organisme: 'Messages reçus par les citoyens',
  theme: 'socle',
});
