import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { Observable } from 'rxjs';

export type Mode = 'NORMAL' | 'LENT' | 'ERREURS' | 'MUET';

/** État du registre vu par Sira. Les écrans des agents, eux, répondent toujours. */
export interface Panne {
  mode: Mode;
  /** Attente ajoutée à chaque réponse en mode `LENT`. */
  delai_ms: number;
  modifiee_le: string;
}

export const MODES: readonly Mode[] = ['NORMAL', 'LENT', 'ERREURS', 'MUET'];

export const LIBELLES_MODE: Record<Mode, string> = {
  NORMAL: 'Normal',
  LENT: 'Lent',
  ERREURS: 'En erreur',
  MUET: 'Muet',
};

const CHEMIN = '/api/panne';

@Injectable({ providedIn: 'root' })
export class Pannes {
  private readonly api = inject(Api);

  lire(): Observable<Panne> {
    return this.api.lire(CHEMIN);
  }

  changer(mode: Mode, delaiMs?: number): Observable<Panne> {
    return this.api.remplacer(CHEMIN, { mode, delai_ms: delaiMs });
  }
}
