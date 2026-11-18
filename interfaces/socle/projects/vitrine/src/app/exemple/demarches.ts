import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';

/** Les modèles reprennent les noms du JSON du serveur, sans conversion. */
export interface Demarche {
  id: string;
  nom: string;
  organisme: string;
  finalite: string;
  duree_jours: number;
  etat: 'ACTIVE' | 'SUSPENDUE';
  cree_le: string;
}

export type NouvelleDemarche = Pick<Demarche, 'nom' | 'organisme' | 'finalite' | 'duree_jours'>;

const CHEMIN = '/api/exemple/demarches';

/** Un service par ressource : il connaît les chemins, les écrans ne les écrivent jamais. */
@Injectable({ providedIn: 'root' })
export class Demarches {
  private readonly api = inject(Api);

  lister(page: number, recherche: string): Observable<Page<Demarche>> {
    return this.api.lire(CHEMIN, { page, taille: 5, recherche });
  }

  lire(id: string): Observable<Demarche> {
    return this.api.lire(`${CHEMIN}/${encodeURIComponent(id)}`);
  }

  creer(demarche: NouvelleDemarche): Observable<Demarche> {
    return this.api.creer(CHEMIN, demarche);
  }

  supprimer(id: string): Observable<void> {
    return this.api.supprimer(`${CHEMIN}/${encodeURIComponent(id)}`);
  }
}
