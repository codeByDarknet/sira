import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';
import { Mode } from '../pannes/panne';

/** Un appel reçu de Sira. Il ne dit pas quelle personne était concernée. */
export interface Appel {
  /** Identifiant (UUID). */
  id: string;
  horodatage: string;
  type: 'FICHE' | 'DOCUMENT';
  /** État du registre au moment de l'appel. */
  mode: Mode;
  /** Statut HTTP rendu ; absent si le registre n'a pas répondu. */
  statut: number | null;
  duree_ms: number;
  origine: string;
}

@Injectable({ providedIn: 'root' })
export class Appels {
  private readonly api = inject(Api);

  lister(page: number): Observable<Page<Appel>> {
    return this.api.lire('/api/appels', { page, taille: 20 });
  }
}
