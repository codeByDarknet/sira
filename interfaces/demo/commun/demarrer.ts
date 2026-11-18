import { Type } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { Routes } from '@angular/router';
import { fournirCoquille } from '@sira/coquille';
import { CONFIGURATION, ConfigurationDemo, lireConfiguration } from './configuration';

/**
 * Démarrage commun aux applications de démonstration : la configuration est lue avant le
 * premier affichage, pour que le thème et le nom soient justes dès la première image.
 */
export async function demarrer(
  racine: Type<unknown>,
  routes: Routes,
  defauts: ConfigurationDemo,
): Promise<void> {
  const configuration = await lireConfiguration(defauts);
  document.documentElement.dataset['theme'] = configuration.theme;
  try {
    await bootstrapApplication(racine, {
      providers: [
        fournirCoquille({ application: configuration.nom, routes }),
        { provide: CONFIGURATION, useValue: configuration },
      ],
    });
  } catch (erreur) {
    // eslint-disable-next-line no-console -- dernier recours : l'application n'a pas démarré
    console.error(erreur);
  }
}
