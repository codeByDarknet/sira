import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { Observable } from 'rxjs';
import { Libelle } from '../commun/libelles';

export type Disjoncteur = 'FERME' | 'OUVERT' | 'SEMI_OUVERT';

/** Santé de la liaison entre la passerelle et un registre. */
export interface EtatRegistre {
  code: string;
  nom: string;
  disjoncteur: Disjoncteur;
  /** Réponses encore attendues de ce registre. */
  en_file: number;
  dernier_succes_le: string | null;
  dernier_echec_le: string | null;
}

export interface EtatReseau {
  registres: EtatRegistre[];
  file: { a_traiter: number; plus_ancien_le: string | null };
}

export interface Statistiques {
  demandes_jour: number;
  accords_jour: number;
  refus_jour: number;
  consultations_jour: number;
  pieces_evitees_total: number;
}

/** Le disjoncteur dit en clair si le registre répond ; la couleur ne porte jamais seule le sens. */
export const LIBELLES_DISJONCTEUR: Record<Disjoncteur, Libelle> = {
  FERME: { texte: 'Répond normalement', etat: 'succes' },
  SEMI_OUVERT: { texte: 'Reprise en cours', etat: 'attention' },
  OUVERT: { texte: 'Ne répond pas', etat: 'erreur' },
};

@Injectable({ providedIn: 'root' })
export class Reseau {
  private readonly api = inject(Api);

  etat(): Observable<EtatReseau> {
    return this.api.lire('/api/etat');
  }

  statistiques(): Observable<Statistiques> {
    return this.api.lire('/api/statistiques');
  }
}
