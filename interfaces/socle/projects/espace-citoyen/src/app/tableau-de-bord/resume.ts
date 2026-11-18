import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { Observable, forkJoin, map } from 'rxjs';
import { Demande, Resume } from '../commun/modeles';
import { Demandes } from '../demandes/demandes';

export interface Situation {
  resume: Resume;
  demandes: Demande[];
}

/** Ce que l'accueil montre d'un coup d'œil : les chiffres, et les demandes qui attendent. */
@Injectable({ providedIn: 'root' })
export class Situations {
  private readonly api = inject(Api);
  private readonly demandes = inject(Demandes);

  lire(): Observable<Situation> {
    return forkJoin({
      resume: this.api.lire<Resume>('/api/resume'),
      demandes: this.demandes.enAttente().pipe(map((page) => page.elements)),
    });
  }
}
