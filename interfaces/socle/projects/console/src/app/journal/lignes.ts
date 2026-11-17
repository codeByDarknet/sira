import { Injectable, inject } from '@angular/core';
import { Api, Page, Parametres } from '@sira/coquille';
import { formaterDateHeure } from '@sira/ui';
import { Observable } from 'rxjs';
import { Libelle } from '../commun/libelles';
import { Niveau } from '../jeux/jeux';

export type TypeLigne =
  | 'DEMANDE'
  | 'ACCORD'
  | 'REFUS'
  | 'CONSULTATION'
  | 'ECHEC'
  | 'REUTILISATION'
  | 'RETRAIT'
  | 'SIGNALEMENT'
  | 'ADMINISTRATION';

export type Resultat = 'REUSSI' | 'REFUSE' | 'ECHEC' | 'EN_ATTENTE';

/** Un objet cité par une ligne : son code seul, ou son code avec son nom. */
export type Reference = string | { code: string; nom?: string; libelle?: string };

/**
 * Une ligne du journal vue par l'administration. Elle ne porte jamais la valeur transmise ;
 * le citoyen n'y est désigné que par un pseudonyme de journal tronqué.
 */
export interface LigneJournal {
  id: string;
  horodatage: string;
  type: TypeLigne;
  resultat: Resultat;
  pseudonyme?: string | null;
  service?: Reference | null;
  registre?: Reference | null;
  jeu?: (Exclude<Reference, string> & { niveau?: Niveau }) | string | null;
  niveau?: Niveau | null;
  demarche?: Reference | null;
  finalite?: string | null;
  canal?: string | null;
  id_echange?: string | null;
  /** Pour une ligne d'administration : qui a agi, et sur quoi. */
  acteur?: string | null;
  objet?: string | null;
  detail?: string | null;
}

export const LIBELLES_TYPE: Record<TypeLigne, string> = {
  DEMANDE: 'Demande d’un service',
  ACCORD: 'Accord du citoyen',
  REFUS: 'Refus du citoyen',
  CONSULTATION: 'Consultation d’un registre',
  ECHEC: 'Échec d’une consultation',
  REUTILISATION: 'Réutilisation d’une réponse',
  RETRAIT: 'Retrait d’un accès',
  SIGNALEMENT: 'Signalement d’un citoyen',
  ADMINISTRATION: 'Action d’administration',
};

/** Un type que cet écran ne connaît pas encore s'affiche tel quel plutôt que de disparaître. */
export function libelleType(type: string): string {
  return (LIBELLES_TYPE as Record<string, string>)[type] ?? type;
}

const LIBELLES_RESULTAT: Record<Resultat, Libelle> = {
  REUSSI: { texte: 'Réussi', etat: 'succes' },
  REFUSE: { texte: 'Refusé', etat: 'erreur' },
  ECHEC: { texte: 'Échec', etat: 'erreur' },
  EN_ATTENTE: { texte: 'En attente', etat: 'attention' },
};

export function libelleResultat(resultat: string): Libelle {
  return (
    (LIBELLES_RESULTAT as Record<string, Libelle>)[resultat] ?? { texte: resultat, etat: 'neutre' }
  );
}

const LIBELLES_CANAL: Record<string, string> = {
  REDIRECTION: 'Site du service',
  CODE: 'Code ou QR code',
  NOTIFICATION: 'Espace citoyen',
};

export function libelleCanal(canal: string): string {
  return LIBELLES_CANAL[canal] ?? canal;
}

/** Nom lisible d'un objet cité, ou son code à défaut ; vide s'il n'y en a pas. */
export function nomDe(reference: Reference | null | undefined): string {
  if (!reference) {
    return '';
  }
  return typeof reference === 'string'
    ? reference
    : (reference.nom ?? reference.libelle ?? reference.code);
}

/** Niveau de la question citée, qu'il soit porté par la ligne ou par la question. */
export function niveauDe(ligne: LigneJournal): Niveau | null {
  const jeu = ligne.jeu;
  return ligne.niveau ?? (typeof jeu === 'object' && jeu !== null ? (jeu.niveau ?? null) : null);
}

export interface PointDeControle {
  horodatage: string;
  ligne: number;
  jws: string;
}

/** Première ligne dont le hachage ne correspond plus : son numéro, ou sa description. */
/** Numéro de la première ligne dont le chaînage ne se vérifie pas. */
export type Rupture = number;

export interface Verification {
  valide: boolean;
  lignes: number;
  dernier_hachage: string | null;
  dernier_point_de_controle: PointDeControle | null;
  premiere_rupture: Rupture | null;
}

export interface CompteRendu {
  valide: boolean;
  titre: string;
  phrases: string[];
}

const NOMBRE = new Intl.NumberFormat('fr-FR');

function designer(rupture: Rupture | null): string {
  if (rupture === null) {
    return 'une ligne non précisée';
  }
  return `la ligne n° ${NOMBRE.format(rupture)}`;
}

/** Le résultat de la vérification, en phrases : intact, ou rompu et à partir d'où. */
export function compteRendu(verification: Verification): CompteRendu {
  const lignes =
    verification.lignes === 1
      ? '1 ligne vérifiée.'
      : `${NOMBRE.format(verification.lignes)} lignes vérifiées.`;
  const point = verification.dernier_point_de_controle;
  const controle = point
    ? `Dernier point de contrôle signé : ligne n° ${point.ligne}, le ${formaterDateHeure(point.horodatage)}.`
    : 'Aucun point de contrôle signé.';
  if (verification.valide) {
    return {
      valide: true,
      titre: 'Le journal est intact',
      phrases: [`${lignes} Aucune modification.`, controle],
    };
  }
  return {
    valide: false,
    titre: 'Le journal a été altéré',
    phrases: [
      `Chaîne rompue à partir de ${designer(verification.premiere_rupture)} : les suivantes ne sont plus fiables.`,
      lignes,
      controle,
      'Prévenez sans attendre le responsable de la sécurité.',
    ],
  };
}

@Injectable({ providedIn: 'root' })
export class Journal {
  private readonly api = inject(Api);

  chercher(parametres: Parametres): Observable<Page<LigneJournal>> {
    return this.api.lire('/api/journal', parametres);
  }

  lister(page: number, filtres: Parametres): Observable<Page<LigneJournal>> {
    return this.api.lire('/api/journal', { ...filtres, page, taille: 20 });
  }

  /** Recalcule toute la chaîne côté serveur. */
  verifier(): Observable<Verification> {
    return this.api.lire('/api/journal/verification');
  }
}
