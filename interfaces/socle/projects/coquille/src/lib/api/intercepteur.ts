import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { EMPTY, catchError, throwError } from 'rxjs';
import { adresseConnexion } from './chemins';
import { CHEMIN_COURANT, REDIRECTION } from './redirection';

/** À poser sur un appel qui veut traiter lui-même une réponse 401 (lecture de la session). */
export const SANS_REDIRECTION = new HttpContextToken<boolean>(() => false);

/**
 * Session absente ou expirée : le serveur répond 401, l'usager est envoyé se connecter puis
 * ramené sur l'écran en cours. L'appel ne se termine pas : la page est en train de changer.
 */
export const intercepteurSession: HttpInterceptorFn = (requete, suite) => {
  const rediriger = inject(REDIRECTION);
  const cheminCourant = inject(CHEMIN_COURANT);
  return suite(requete).pipe(
    catchError((erreur: unknown) => {
      const expiree = erreur instanceof HttpErrorResponse && erreur.status === 401;
      if (!expiree || requete.context.get(SANS_REDIRECTION)) {
        return throwError(() => erreur);
      }
      rediriger(adresseConnexion(cheminCourant()));
      return EMPTY;
    }),
  );
};
