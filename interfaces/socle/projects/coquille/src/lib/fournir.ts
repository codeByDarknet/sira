import {
  HttpInterceptorFn,
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import {
  EnvironmentProviders,
  makeEnvironmentProviders,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {
  Routes,
  TitleStrategy,
  provideRouter,
  PreloadAllModules,
  withComponentInputBinding,
  withPreloading,
  withInMemoryScrolling,
} from '@angular/router';
import { fournirApparence } from './apparence/theme';
import { intercepteurSession } from './api/intercepteur';
import { CHEMIN_ACCES_REFUSE, CHEMIN_INDISPONIBLE } from './session/gardes';
import { NOM_APPLICATION, TitreDePage } from './navigation/titre';

export interface OptionsCoquille {
  /** Nom repris dans le titre des pages ; une fonction quand il vient de la configuration. */
  application: string | (() => string);
  routes: Routes;
  /** Intercepteurs propres à l'application, placés après celui de la session. */
  intercepteurs?: HttpInterceptorFn[];
}

/** Routes d'erreur communes, à placer en dernier : la route `**` attrape tout le reste. */
export const ROUTES_ERREUR: Routes = [
  {
    path: CHEMIN_ACCES_REFUSE.slice(1),
    title: 'Accès refusé',
    loadComponent: () => import('./pages/pages-erreur').then((m) => m.PageAccesRefuse),
  },
  {
    path: CHEMIN_INDISPONIBLE.slice(1),
    title: 'Service indisponible',
    loadComponent: () => import('./pages/pages-erreur').then((m) => m.PageIndisponible),
  },
  {
    path: '**',
    title: 'Page introuvable',
    loadComponent: () => import('./pages/pages-erreur').then((m) => m.PageIntrouvable),
  },
];

/**
 * Tout ce qu'une application Sira branche de la même façon : détection de changement sans
 * zone, routeur, titres de page, accès au serveur avec session et protection CSRF.
 */
export function fournirCoquille(options: OptionsCoquille): EnvironmentProviders {
  const { application } = options;
  return makeEnvironmentProviders([
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    fournirApparence(),
    provideRouter(
      options.routes,
      // Les paramètres de route arrivent dans les entrées du composant (`id = input.required()`).
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
      // Les écrans se chargent en arrière-plan dès l'arrivée : un clic ne fait plus attendre.
      withPreloading(PreloadAllModules),
    ),
    {
      provide: NOM_APPLICATION,
      useValue: typeof application === 'string' ? () => application : application,
    },
    { provide: TitleStrategy, useClass: TitreDePage },
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([intercepteurSession, ...(options.intercepteurs ?? [])]),
    ),
  ]);
}
