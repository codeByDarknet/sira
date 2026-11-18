import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable, catchError, throwError } from 'rxjs';
import { Decision, Demande, ResultatDecision } from '../commun/modeles';

const BASE = '/api/demandes';

/** Demandes d'accès adressées au citoyen connecté. */
@Injectable({ providedIn: 'root' })
export class Demandes {
  private readonly api = inject(Api);

  enAttente(): Observable<Page<Demande>> {
    return this.api.lire(BASE, { etat: 'EN_ATTENTE_ACCORD', page: 1, taille: 20 });
  }

  /**
   * Ouvre une demande pour l'écran d'accord. Une demande arrivée par QR code ou par
   * redirection n'est encore à personne : elle répond 404 jusqu'à son rattachement.
   */
  ouvrir(id: string): Observable<Demande> {
    const chemin = `${BASE}/${encodeURIComponent(id)}`;
    return this.api
      .lire<Demande>(chemin)
      .pipe(
        catchError((erreur: unknown) =>
          erreur instanceof HttpErrorResponse && erreur.status === 404
            ? this.api.creer<Demande>(`${chemin}/rattachement`)
            : throwError(() => erreur),
        ),
      );
  }

  rattacherParCode(codeCourt: string): Observable<Demande> {
    return this.api.creer(`${BASE}/rattachement`, { code_court: codeCourt });
  }

  /** @param codeVerification code recopié depuis l'écran du service, quand l'accord l'exige */
  decider(id: string, decision: Decision, codeVerification?: string): Observable<ResultatDecision> {
    return this.api.creer(`${BASE}/${encodeURIComponent(id)}/decision`, {
      decision,
      ...(codeVerification ? { code_verification: codeVerification } : {}),
    });
  }
}
