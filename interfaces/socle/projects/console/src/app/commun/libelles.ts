import { EtatEtiquette } from '@sira/ui';

export interface Libelle {
  texte: string;
  etat: EtatEtiquette;
}

/** Étiquette « Actif / Désactivé » commune aux membres, registres, questions et démarches. */
export function libelleActif(actif: boolean): Libelle {
  return actif ? { texte: 'Actif', etat: 'succes' } : { texte: 'Désactivé', etat: 'neutre' };
}

/** Code court : minuscules, chiffres et tirets (et points pour les questions). */
export const CODE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/** Choix du filtre « État » des listes (le serveur filtre). */
export const CHOIX_ACTIF = [
  { valeur: '', libelle: 'Tous les états' },
  { valeur: 'true', libelle: 'Actifs' },
  { valeur: 'false', libelle: 'Désactivés' },
];

/** « 1 membre », « 7 membres », « Aucun membre ». */
export function compter(total: number, [un, plusieurs]: readonly [string, string]): string {
  if (total === 0) {
    return `Aucun ${un}`;
  }
  return total === 1 ? `1 ${un}` : `${total.toLocaleString('fr-FR')} ${plusieurs}`;
}
