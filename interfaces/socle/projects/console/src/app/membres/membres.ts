import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Ressource } from '../commun/ressource';

export type RoleMembre = 'SERVICE' | 'REGISTRE';

/** Une administration raccordée au socle. */
export interface Membre {
  code: string;
  nom: string;
  organisme: string;
  roles: RoleMembre[];
  /** Identité portée par son certificat ; attribuée par le socle. */
  identite: string;
  retours_autorises: string[];
  actif: boolean;
}

export type SaisieMembre = Omit<Membre, 'identite'>;

const LIBELLES_ROLE_MEMBRE: Record<RoleMembre, string> = {
  SERVICE: 'Demande des informations',
  REGISTRE: 'Fournit des informations',
};

export function libelleRoleMembre(role: RoleMembre): string {
  return LIBELLES_ROLE_MEMBRE[role];
}

@Injectable({ providedIn: 'root' })
export class Membres extends Ressource<Membre> {
  protected readonly chemin = '/api/membres';

  /** Décision de l'autorité : l'instructeur ne change pas ces adresses. */
  changerRetours(code: string, retours: string[]): Observable<Membre> {
    return this.api.remplacer(this.adresse(code, '/retours'), { retours_autorises: retours });
  }
}
