import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';
import {
  AdressesPiece,
  Canal,
  Dossier,
  EtatDemande,
  EtatDossier,
  adresses,
} from '../parcours/parcours';

export interface LigneDossier {
  id: string;
  numero: string;
  etat: EtatDossier;
  cree_le: string;
  modifie_le: string;
}

export interface DemandeDuDossier {
  id: string;
  canal: Canal;
  origine: 'CITOYEN' | 'AGENT';
  etat: EtatDemande;
  creee_le: string;
}

export interface DetailDossier {
  dossier: Dossier;
  demandes: DemandeDuDossier[];
}

const CHEMIN = '/api/agent/dossiers';

/** Le back-office : les dossiers reçus par le service, sans rien de plus que ce qu'il a reçu. */
@Injectable({ providedIn: 'root' })
export class Agent {
  private readonly api = inject(Api);

  lister(page: number, etat: EtatDossier | ''): Observable<Page<LigneDossier>> {
    return this.api.lire(CHEMIN, { page, taille: 10, etat });
  }

  lire(id: string): Observable<DetailDossier> {
    return this.api.lire(adresse(id));
  }

  /** Envoie une nouvelle demande dans l'espace Sira du citoyen. */
  redemander(id: string): Observable<DemandeDuDossier> {
    return this.api.creer(`${adresse(id)}/verifications`);
  }
}

function adresse(id: string): string {
  return `${CHEMIN}/${encodeURIComponent(id)}`;
}

/** Pièce reçue d'un registre pour une condition d'un dossier. */
export function adressePieceDossier(id: string, jeu: string): AdressesPiece {
  return adresses(`${adresse(id)}/pieces/${encodeURIComponent(jeu)}`);
}

/** Pièce jointe par l'usager à son dossier. */
export function adressePieceDeclareeDossier(id: string, code: string): AdressesPiece {
  return adresses(`${adresse(id)}/declarations/${encodeURIComponent(code)}/piece`);
}
