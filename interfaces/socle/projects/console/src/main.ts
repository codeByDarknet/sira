import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { configuration } from './app/app.config';

bootstrapApplication(App, configuration).catch((erreur: unknown) => {
  // eslint-disable-next-line no-console -- dernier recours : l'application n'a pas démarré
  console.error(erreur);
});
