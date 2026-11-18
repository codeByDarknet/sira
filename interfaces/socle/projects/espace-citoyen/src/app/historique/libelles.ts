import { EtatEtiquette } from '@sira/ui';
import { LigneJournal, TypeLigne } from '../commun/modeles';

/** Filtre par type, dans les mots de l'usager. */
export const TYPES: readonly { type: TypeLigne; libelle: string }[] = [
  { type: 'CONSULTATION', libelle: 'Informations transmises' },
  { type: 'REUTILISATION', libelle: 'Réponses réutilisées' },
  { type: 'DEMANDE', libelle: 'Demandes reçues' },
  { type: 'ACCORD', libelle: 'Accords donnés' },
  { type: 'REFUS', libelle: 'Refus' },
  { type: 'ECHEC', libelle: 'Échecs' },
  { type: 'RETRAIT', libelle: 'Accès retirés' },
  { type: 'SIGNALEMENT', libelle: 'Signalements' },
];

export interface Issue {
  etat: EtatEtiquette;
  texte: string;
  /** Ce que le service a reçu ou non, quand le texte seul ne le dit pas. */
  precision?: string;
}

const RIEN_TRANSMIS = 'Rien n’a été transmis.';

const ISSUES: Record<Exclude<TypeLigne, 'CONSULTATION'>, Issue> = {
  DEMANDE: { etat: 'info', texte: 'Demande reçue' },
  ACCORD: { etat: 'succes', texte: 'Autorisé par vous' },
  REFUS: { etat: 'erreur', texte: 'Refusé par vous', precision: RIEN_TRANSMIS },
  ECHEC: { etat: 'erreur', texte: 'Échec', precision: RIEN_TRANSMIS },
  REUTILISATION: {
    etat: 'succes',
    texte: 'Transmis',
    precision: 'Réponse déjà obtenue, renvoyée.',
  },
  RETRAIT: { etat: 'neutre', texte: 'Accès retiré par vous' },
  SIGNALEMENT: { etat: 'info', texte: 'Signalé par vous' },
};

/** Ce qui s'est passé, en un mot et une couleur : les refus et les échecs se voient autant que le reste. */
export function decrireIssue(ligne: Pick<LigneJournal, 'type' | 'resultat'>): Issue {
  if (ligne.type !== 'CONSULTATION') {
    return ISSUES[ligne.type];
  }
  switch (ligne.resultat) {
    case 'REUSSI':
      return { etat: 'succes', texte: 'Transmis' };
    case 'EN_ATTENTE':
      return {
        etat: 'attention',
        texte: 'En attente',
        precision: 'L’administration n’a pas encore répondu.',
      };
    case 'REFUSE':
      return {
        etat: 'erreur',
        texte: 'Refusé',
        precision: `L’administration a refusé. ${RIEN_TRANSMIS}`,
      };
    default:
      return ISSUES.ECHEC;
  }
}
