import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';
import { LigneJournal } from '../commun/modeles';

const BASE = '/api/journal';
export const TAILLE_PAGE = 20;
export const MOTIF_MINIMUM = 10;
export const MOTIF_MAXIMUM = 500;

export interface FiltreJournal {
  page: number;
  type?: string;
  service?: string;
}

/** Historique des accès aux informations du citoyen connecté. */
@Injectable({ providedIn: 'root' })
export class Journal {
  private readonly api = inject(Api);

  lister(filtre: FiltreJournal): Observable<Page<LigneJournal>> {
    return this.api.lire(BASE, { ...filtre, taille: TAILLE_PAGE });
  }

  signaler(id: string, motif: string): Observable<void> {
    return this.api.creer(`${BASE}/${encodeURIComponent(id)}/signalement`, { motif });
  }
}
