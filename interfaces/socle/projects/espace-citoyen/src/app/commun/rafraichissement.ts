import { DOCUMENT, inject } from '@angular/core';
import { EMPTY, Observable, catchError, filter, switchMap, tap, throwError, timer } from 'rxjs';

/** Délai entre deux lectures d'une liste qui se tient à jour seule. */
export const PERIODE_RAFRAICHISSEMENT = 5000;

/**
 * Relit une ressource à intervalle régulier, sans bruit : rien n'est demandé quand l'onglet
 * est caché, et une lecture ratée après la première laisse l'écran tel qu'il est.
 * À appeler dans un contexte d'injection.
 */
export function suivre(): <T>(lire: () => Observable<T>) => Observable<T> {
  const document = inject(DOCUMENT);
  return (lire) => {
    let dejaLu = false;
    return timer(0, PERIODE_RAFRAICHISSEMENT).pipe(
      filter(() => !dejaLu || document.visibilityState === 'visible'),
      switchMap(() =>
        lire().pipe(catchError((erreur: unknown) => (dejaLu ? EMPTY : throwError(() => erreur)))),
      ),
      tap(() => (dejaLu = true)),
    );
  };
}
