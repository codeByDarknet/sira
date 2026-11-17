import { Jeu } from '../jeux/jeux';
import { Decision, Habilitation } from './habilitations';

/*
 * Qui peut faire quoi sur une habilitation, et le rappel de minimisation. L'interface s'en
 * sert pour ne montrer que les gestes permis ; le serveur reste seul juge.
 */

/** Ce que le compte connecté sait faire. */
export interface Acteur {
  nom: string;
  /** Rôle instructeur. */
  ecriture: boolean;
  /** Rôle autorité. */
  decision: boolean;
}

export interface GestesPermis {
  modifier: boolean;
  soumettre: boolean;
  decisions: Decision[];
  /** Pourquoi aucun geste n'est proposé, ou ce qui se passera ensuite. */
  explication: string | null;
}

const AUCUN_GESTE: Omit<GestesPermis, 'explication'> = {
  modifier: false,
  soumettre: false,
  decisions: [],
};

/** Gestes proposés à l'acteur sur cette habilitation, d'après son rôle et l'état du dossier. */
export function gestesPermis(habilitation: Habilitation, acteur: Acteur): GestesPermis {
  switch (habilitation.etat) {
    case 'BROUILLON':
      return acteur.ecriture
        ? { ...AUCUN_GESTE, modifier: true, soumettre: true, explication: null }
        : {
            ...AUCUN_GESTE,
            explication: 'Brouillon non soumis. Seul un instructeur peut le compléter.',
          };
    case 'SOUMISE':
      return decisionsOuExplication(
        habilitation,
        acteur,
        ['approbation', 'rejet'],
        'En attente de l’autorité. Votre rôle ne permet pas de décider.',
      );
    case 'APPROUVEE':
      return decisionsOuExplication(habilitation, acteur, ['suspension'], '');
    case 'REJETEE':
      return {
        ...AUCUN_GESTE,
        explication: 'Dossier clos. Pour une nouvelle demande, préparez une autre habilitation.',
      };
    case 'SUSPENDUE':
      return {
        ...AUCUN_GESTE,
        explication: 'Accès suspendu. Pour le rétablir, préparez une autre habilitation.',
      };
  }
}

function decisionsOuExplication(
  habilitation: Habilitation,
  acteur: Acteur,
  decisions: Decision[],
  sansRole: string,
): GestesPermis {
  if (!acteur.decision) {
    return { ...AUCUN_GESTE, explication: sansRole };
  }
  if (habilitation.instruite_par === acteur.nom) {
    return {
      ...AUCUN_GESTE,
      explication: 'Vous l’avez préparé : une autre personne ayant le rôle d’autorité décide.',
    };
  }
  return { ...AUCUN_GESTE, decisions, explication: null };
}

/**
 * Questions cochées qui livrent une information ou un document alors que le même registre
 * propose une question en « oui ou non » : l'instructeur doit s'assurer que celle-ci ne suffit pas.
 */
export function questionsARevoir(choisies: readonly string[], publiees: readonly Jeu[]): Jeu[] {
  const registresAvecOuiNon = new Set(
    publiees.filter((jeu) => jeu.niveau === 'STATUT').map((jeu) => jeu.registre),
  );
  return publiees.filter(
    (jeu) =>
      choisies.includes(jeu.code) &&
      jeu.niveau !== 'STATUT' &&
      registresAvecOuiNon.has(jeu.registre),
  );
}

/** Les questions regroupées par registre, dans l'ordre des codes de registre. */
export function parRegistre(jeux: readonly Jeu[]): { registre: string; jeux: Jeu[] }[] {
  const groupes = new Map<string, Jeu[]>();
  for (const jeu of jeux) {
    groupes.set(jeu.registre, [...(groupes.get(jeu.registre) ?? []), jeu]);
  }
  return [...groupes.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([registre, liste]) => ({ registre, jeux: liste }));
}
