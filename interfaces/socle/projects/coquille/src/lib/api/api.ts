import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, retry, throwError, timer } from 'rxjs';

/** Valeurs de recherche ; les valeurs absentes ne sont pas envoyées. */
export type Parametres = Record<string, string | number | boolean | null | undefined>;

/** Page de résultats renvoyée par les listes du serveur ; `page` commence à 1. */
export interface Page<T> {
  elements: T[];
  page: number;
  taille: number;
  total: number;
}

/** Lecture qui échoue parce qu'un serveur démarre ou met trop de temps : on réessaie. */
const PASSAGERES = new Set([0, 502, 503, 504]);
export const ESSAIS_LECTURE = 2;
export const ATTENTE_LECTURE_MS = 800;

export function nombreDePages(page: Pick<Page<unknown>, 'taille' | 'total'>): number {
  return Math.max(1, Math.ceil(page.total / page.taille));
}

/**
 * Accès au serveur d'application. Les écrans n'utilisent pas `HttpClient` directement :
 * tous les appels partent d'ici, sur la même origine, avec le cookie de session ; le jeton
 * CSRF (cookie `XSRF-TOKEN`) repart dans l'en-tête `X-XSRF-TOKEN` des appels qui modifient.
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  lire<T>(chemin: string, parametres: Parametres = {}, contexte?: HttpContext): Observable<T> {
    let params = new HttpParams();
    for (const [cle, valeur] of Object.entries(parametres)) {
      if (valeur !== null && valeur !== undefined && valeur !== '') {
        params = params.set(cle, valeur);
      }
    }
    // Une lecture ne change rien : la refaire est sans risque. Un serveur qui vient de démarrer
    // répond lentement aux premiers appels ; deux nouveaux essais évitent une erreur affichée.
    return this.http.get<T>(chemin, { params, context: contexte }).pipe(
      retry({
        count: ESSAIS_LECTURE,
        delay: (erreur: unknown, essai: number) =>
          erreur instanceof HttpErrorResponse && PASSAGERES.has(erreur.status)
            ? timer(ATTENTE_LECTURE_MS * essai)
            : throwError(() => erreur),
      }),
    );
  }

  creer<T>(chemin: string, corps: unknown = null): Observable<T> {
    return this.http.post<T>(chemin, corps);
  }

  remplacer<T>(chemin: string, corps: unknown): Observable<T> {
    return this.http.put<T>(chemin, corps);
  }

  /** Modification partielle : seuls les champs envoyés changent. */
  modifier<T>(chemin: string, corps: unknown): Observable<T> {
    return this.http.patch<T>(chemin, corps);
  }

  supprimer<T = void>(chemin: string): Observable<T> {
    return this.http.delete<T>(chemin);
  }
}
