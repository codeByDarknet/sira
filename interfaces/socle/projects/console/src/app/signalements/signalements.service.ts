import { Injectable, inject } from '@angular/core';
import { Api, Parametres, Page } from '@sira/coquille';
import { Observable, map } from 'rxjs';
import { LigneJournal } from '../journal/lignes';

/** Un citoyen conteste un accès à ses informations, lu dans son historique. */
export interface Signalement {
  id: string;
  /** La ligne de journal contestée, jointe par la passerelle. */
  ligne: LigneJournal;
  motif: string;
  cree_le: string;
  reponse: string | null;
  traite_par: string | null;
  traite_le: string | null;
}

export type EtatSignalement = 'A_TRAITER' | 'TRAITE';

export function estTraite(signalement: Signalement): boolean {
  return signalement.traite_le !== null && signalement.traite_le !== undefined;
}

/** Longueur admise pour la réponse faite au citoyen. */
export const REPONSE = { minimum: 10, maximum: 1000 } as const;

export function verifierReponse(reponse: string): string | null {
  const longueur = reponse.trim().length;
  if (longueur < REPONSE.minimum) {
    return 'Écrivez au moins une phrase.';
  }
  return longueur > REPONSE.maximum
    ? `Raccourcissez votre réponse : ${REPONSE.maximum} caractères au plus.`
    : null;
}

@Injectable({ providedIn: 'root' })
export class Signalements {
  private readonly api = inject(Api);
  private readonly chemin = '/api/signalements';

  chercher(parametres: Parametres): Observable<Page<Signalement>> {
    return this.api.lire(this.chemin, parametres);
  }

  lister(page: number, etat: EtatSignalement | ''): Observable<Page<Signalement>> {
    return this.api.lire(this.chemin, { etat, page, taille: 20 });
  }

  /** Nombre de signalements qui attendent une réponse. */
  compterATraiter(): Observable<number> {
    return this.api
      .lire<Page<Signalement>>(this.chemin, { etat: 'A_TRAITER', page: 1, taille: 1 })
      .pipe(map((page) => page.total));
  }

  traiter(id: string, reponse: string): Observable<Signalement> {
    return this.api.creer(`${this.chemin}/${encodeURIComponent(id)}/traitement`, { reponse });
  }
}
