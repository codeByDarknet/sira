import { demarrer } from '@demo/commun';
import { App } from './app/app';
import { routes } from './app/app.routes';

// Valeurs utilisées si `/config.json` manque ; en conteneur, les variables CONFIG_* les remplacent.
void demarrer(App, routes, {
  nom: 'Registre',
  organisme: 'Administration',
  theme: 'etat-civil',
});
