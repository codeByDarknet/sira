import { Injectable } from '@angular/core';
import { Ressource } from '../commun/ressource';

/** Un service rendu au citoyen ; elle porte la finalité de l'accès aux informations. */
export interface Demarche {
  code: string;
  libelle: string;
  finalite: string;
  base_legale: string;
  /** Code du membre qui rend ce service. */
  service: string;
  /** Durée de l'accord du citoyen ; 0 pour un usage unique. */
  duree_autorisation_jours: number;
  actif: boolean;
}

/** « Une seule consultation » ou « 30 jours ». */
export function phraseDureeAutorisation(jours: number): string {
  if (jours === 0) {
    return 'Une seule consultation';
  }
  return jours === 1 ? '1 jour' : `${jours} jours`;
}

@Injectable({ providedIn: 'root' })
export class Demarches extends Ressource<Demarche> {
  protected readonly chemin = '/api/demarches';
}
