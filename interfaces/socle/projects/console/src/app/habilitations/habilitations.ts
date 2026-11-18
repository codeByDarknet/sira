import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Libelle } from '../commun/libelles';
import { Ressource } from '../commun/ressource';

export type EtatHabilitation = 'BROUILLON' | 'SOUMISE' | 'APPROUVEE' | 'REJETEE' | 'SUSPENDUE';

/** La décision qui autorise un service à poser une liste de questions pour une démarche. */
export interface Habilitation {
  id: string;
  /** Code du membre qui demande. */
  service: string;
  demarche: string;
  /** Codes des questions autorisées. */
  jeux: string[];
  etat: EtatHabilitation;
  justification: string;
  /** Motif du rejet ou de la suspension. */
  motif?: string | null;
  instruite_par: string;
  decidee_par: string | null;
  decidee_le: string | null;
}

export type SaisieHabilitation = Pick<
  Habilitation,
  'service' | 'demarche' | 'jeux' | 'justification'
>;

export const ETATS_HABILITATION: readonly EtatHabilitation[] = [
  'SOUMISE',
  'BROUILLON',
  'APPROUVEE',
  'REJETEE',
  'SUSPENDUE',
];

const LIBELLES_ETAT: Record<EtatHabilitation, Libelle> = {
  BROUILLON: { texte: 'Brouillon', etat: 'neutre' },
  SOUMISE: { texte: 'À décider', etat: 'attention' },
  APPROUVEE: { texte: 'Approuvée', etat: 'succes' },
  REJETEE: { texte: 'Rejetée', etat: 'erreur' },
  SUSPENDUE: { texte: 'Suspendue', etat: 'erreur' },
};

export function libelleEtat(etat: EtatHabilitation): Libelle {
  return LIBELLES_ETAT[etat];
}

export type Decision = 'approbation' | 'rejet' | 'suspension';

@Injectable({ providedIn: 'root' })
export class Habilitations extends Ressource<Habilitation> {
  protected readonly chemin = '/api/habilitations';

  /** Transmet le brouillon à l'autorité ; il ne se modifie plus ensuite. */
  soumettre(id: string): Observable<Habilitation> {
    return this.api.creer(this.adresse(id, '/soumission'));
  }

  /** Le rejet et la suspension portent un motif ; l'approbation n'en a pas. */
  decider(id: string, decision: Decision, motif?: string): Observable<Habilitation> {
    return this.api.creer(this.adresse(id, `/${decision}`), motif ? { motif } : null);
  }
}
