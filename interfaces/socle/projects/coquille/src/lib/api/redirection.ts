import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/** Navigation pleine page hors du routeur (connexion, déconnexion). Remplaçable dans les tests. */
export const REDIRECTION = new InjectionToken<(adresse: string) => void>('REDIRECTION', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return (adresse) => document.location.assign(adresse);
  },
});

/** Chemin de l'écran affiché, recherche comprise : c'est là que la connexion ramènera l'usager. */
export const CHEMIN_COURANT = new InjectionToken<() => string>('CHEMIN_COURANT', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return () => document.location.pathname + document.location.search;
  },
});
