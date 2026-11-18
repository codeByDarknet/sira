import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Ressource } from '../commun/ressource';

export type Niveau = 'STATUT' | 'CHAMPS' | 'DOCUMENT';

export type Operateur =
  | 'EGAL'
  | 'DIFFERENT'
  | 'AU_MOINS'
  | 'AU_PLUS'
  | 'AGE_AU_MOINS'
  | 'AGE_AU_PLUS'
  | 'EXISTE'
  | 'AUCUN'
  | 'ENCORE_VALIDE';

/** Condition évaluée par le connecteur ; le service n'en reçoit que « oui » ou « non ». */
export interface Regle {
  champ: string;
  operateur: Operateur;
  valeur?: string | number | boolean;
}

/** Une « question » : ce qu'un registre accepte de fournir, et sous quelle forme. */
export interface Jeu {
  code: string;
  registre: string;
  niveau: Niveau;
  /** Affiché au citoyen sur l'écran d'accord. */
  libelle: string;
  description?: string;
  /** Durée ISO 8601 pendant laquelle une réponse reste réutilisable : `P90D`. */
  validite: string;
  regle?: Regle;
  champs?: string[];
  document?: string;
  actif: boolean;
}

/** Réponse d'un essai : uniquement ce que le niveau demandé laisse sortir du registre. */
export interface ResultatEssai {
  resultat: {
    valeur?: boolean;
    champs?: Record<string, string>;
    document?: { nom: string; type: string };
  };
}

@Injectable({ providedIn: 'root' })
export class Jeux extends Ressource<Jeu> {
  protected readonly chemin = '/api/jeux';

  /** Évalue la question sur une personne fictive, sans rien publier. */
  essayer(jeu: Jeu, identifiantUnique: string): Observable<ResultatEssai> {
    return this.api.creer(`${this.chemin}/essai`, { jeu, identifiant_unique: identifiantUnique });
  }
}
