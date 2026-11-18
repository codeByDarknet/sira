import { Erreurs } from '../commun/envoi';
import type { ChampSchema, TypeChamp } from '../registres/registres';
import { Jeu, Niveau, Operateur, Regle, ResultatEssai } from './jeux';

/*
 * Tout ce que l'assistant de création sait d'une question, sans affichage : les mots qui
 * décrivent une règle, la vérification de chaque étape et la forme envoyée au serveur.
 */

export interface DescriptionNiveau {
  code: Niveau;
  libelle: string;
  /** Ce que le service reçoit, dit simplement. */
  explication: string;
}

/** Du moins au plus révélateur : c'est l'ordre dans lequel on les propose. */
export const NIVEAUX: readonly DescriptionNiveau[] = [
  {
    code: 'STATUT',
    libelle: 'Oui ou non',
    explication: 'Jamais l’information elle-même.',
  },
  {
    code: 'CHAMPS',
    libelle: 'Quelques informations',
    explication: 'La valeur des informations choisies.',
  },
  {
    code: 'DOCUMENT',
    libelle: 'Un document',
    explication: 'Un document PDF complet.',
  },
];

export function libelleNiveau(niveau: Niveau): string {
  return NIVEAUX.find((description) => description.code === niveau)?.libelle ?? niveau;
}

export interface DescriptionOperateur {
  code: Operateur;
  libelle: string;
  exemple: string;
  /** Ce qu'il faut saisir en plus de l'information : rien, un nombre ou un texte. */
  valeur: 'aucune' | 'nombre' | 'texte';
  libelleValeur?: string;
}

/** Les neuf opérateurs du contrat, dans les mots d'un agent qui ne programme pas. */
export const OPERATEURS: readonly DescriptionOperateur[] = [
  {
    code: 'AGE_AU_MOINS',
    libelle: 'Âge au moins',
    exemple: 'La personne a 18 ans ou plus.',
    valeur: 'nombre',
    libelleValeur: 'Âge minimal, en années',
  },
  {
    code: 'AGE_AU_PLUS',
    libelle: 'Âge au plus',
    exemple: 'La personne a 35 ans ou moins.',
    valeur: 'nombre',
    libelleValeur: 'Âge maximal, en années',
  },
  {
    code: 'EGAL',
    libelle: 'Égal à',
    exemple: 'La nationalité est «\u00a0burkinabè\u00a0».',
    valeur: 'texte',
    libelleValeur: 'Valeur attendue',
  },
  {
    code: 'DIFFERENT',
    libelle: 'Différent de',
    exemple: 'La nationalité n’est pas « ivoirienne ».',
    valeur: 'texte',
    libelleValeur: 'Valeur exclue',
  },
  {
    code: 'AU_MOINS',
    libelle: 'Au moins',
    exemple: 'La moyenne est de 12 ou plus.',
    valeur: 'nombre',
    libelleValeur: 'Valeur minimale',
  },
  {
    code: 'AU_PLUS',
    libelle: 'Au plus',
    exemple: 'Le revenu déclaré est de 500 000 ou moins.',
    valeur: 'nombre',
    libelleValeur: 'Valeur maximale',
  },
  {
    code: 'EXISTE',
    libelle: 'Existe',
    exemple: 'Un diplôme est enregistré.',
    valeur: 'aucune',
  },
  {
    code: 'AUCUN',
    libelle: 'Aucun',
    exemple: 'Aucune condamnation n’est inscrite.',
    valeur: 'aucune',
  },
  {
    code: 'ENCORE_VALIDE',
    libelle: 'Encore valide',
    exemple: 'La date de fin de validité n’est pas dépassée.',
    valeur: 'aucune',
  },
];

export function decrireOperateur(operateur: Operateur | ''): DescriptionOperateur | undefined {
  return OPERATEURS.find((description) => description.code === operateur);
}

/** Les conditions qui ont un sens pour chaque nature d'information. */
const OPERATEURS_PAR_TYPE: Record<TypeChamp, Operateur[]> = {
  TEXTE: ['EGAL', 'DIFFERENT', 'EXISTE', 'AUCUN'],
  DATE: ['AGE_AU_MOINS', 'AGE_AU_PLUS', 'AU_MOINS', 'AU_PLUS', 'ENCORE_VALIDE', 'EXISTE'],
  NOMBRE: ['AU_MOINS', 'AU_PLUS', 'EGAL', 'DIFFERENT'],
  BOOLEEN: ['EGAL'],
};

/** Pour une date, « au moins » et « au plus » se disent « après » et « avant ». */
const SUR_UNE_DATE: Partial<Record<Operateur, Partial<DescriptionOperateur>>> = {
  AU_MOINS: {
    libelle: 'Date le ou après le',
    exemple: 'Le diplôme a été obtenu le 1er janvier 2020 ou après.',
    libelleValeur: 'Date',
  },
  AU_PLUS: {
    libelle: 'Date le ou avant le',
    exemple: 'La personne est née le 31 décembre 2007 ou avant.',
    libelleValeur: 'Date',
  },
};

/** Pour un oui ou non, « égal à » se dit « vaut ». */
const SUR_UN_BOOLEEN: Partial<DescriptionOperateur> = {
  libelle: 'Vaut',
  exemple: 'Le baccalauréat est obtenu : oui.',
  libelleValeur: 'Réponse attendue',
};

/** La description d'une condition, dite selon la nature de l'information. */
export function decrireOperateurPour(
  operateur: Operateur | '',
  type: TypeChamp | undefined,
): DescriptionOperateur | undefined {
  const base = decrireOperateur(operateur);
  if (!base) {
    return undefined;
  }
  if (type === 'DATE' && SUR_UNE_DATE[base.code]) {
    return { ...base, ...SUR_UNE_DATE[base.code] };
  }
  if (type === 'BOOLEEN' && base.code === 'EGAL') {
    return { ...base, ...SUR_UN_BOOLEEN };
  }
  return base;
}

/** Conditions proposées pour une information : celles du type, limitées par le registre. */
export function operateursPour(champ: ChampSchema | undefined): DescriptionOperateur[] {
  if (!champ) {
    return [];
  }
  const admis = champ.operateurs?.length ? champ.operateurs : null;
  return OPERATEURS_PAR_TYPE[champ.type]
    .filter((code) => !admis || admis.includes(code))
    .concat(admis ? admis.filter((code) => !OPERATEURS_PAR_TYPE[champ.type].includes(code)) : [])
    .map((code) => decrireOperateurPour(code, champ.type))
    .filter((description): description is DescriptionOperateur => !!description);
}

/** Forme de la valeur à saisir : aucune, une liste imposée, oui/non, une date, un nombre, un texte. */
export type FormeValeur = 'aucune' | 'liste' | 'booleen' | 'date' | 'nombre' | 'texte';

export function formeValeur(
  champ: ChampSchema | undefined,
  operateur: DescriptionOperateur | undefined,
): FormeValeur {
  if (!operateur || operateur.valeur === 'aucune') {
    return 'aucune';
  }
  if (champ?.valeurs?.length) {
    return 'liste';
  }
  if (champ?.type === 'BOOLEEN') {
    return 'booleen';
  }
  if (champ?.type === 'DATE' && (operateur.code === 'AU_MOINS' || operateur.code === 'AU_PLUS')) {
    return 'date';
  }
  return operateur.valeur;
}

function dire(valeur: Regle['valeur']): string {
  if (typeof valeur === 'boolean') {
    return valeur ? 'oui' : 'non';
  }
  if (typeof valeur === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valeur)) {
    const [annee, mois, jour] = valeur.split('-');
    return `${jour}/${mois}/${annee}`;
  }
  return String(valeur);
}

/** La règle dite en une phrase : c'est la condition pour que la réponse soit « oui ». */
export function phraseRegle(
  { champ, operateur, valeur }: Regle,
  libelles: Record<string, string> = {},
): string {
  const information = `«\u00a0${libelles[champ] ?? champ}\u00a0»`;
  switch (operateur) {
    case 'AGE_AU_MOINS':
      return `la personne a au moins ${valeur} ans, d’après ${information}`;
    case 'AGE_AU_PLUS':
      return `la personne a au plus ${valeur} ans, d’après ${information}`;
    case 'EGAL':
      return `${information} vaut «\u00a0${dire(valeur)}\u00a0»`;
    case 'DIFFERENT':
      return `${information} ne vaut pas «\u00a0${dire(valeur)}\u00a0»`;
    case 'AU_MOINS':
      return `${information} vaut au moins ${dire(valeur)}`;
    case 'AU_PLUS':
      return `${information} vaut au plus ${dire(valeur)}`;
    case 'EXISTE':
      return `${information} est renseigné`;
    case 'AUCUN':
      return `rien n’est inscrit dans ${information}`;
    case 'ENCORE_VALIDE':
      return `la date ${information} n’est pas dépassée`;
  }
}

/** Ce qu'une question laisse sortir du registre, en une phrase. */
export function phraseContenu(
  jeu: Pick<Jeu, 'niveau' | 'regle' | 'champs' | 'document'>,
  libelles: Record<string, string> = {},
): string {
  if (jeu.niveau === 'STATUT' && jeu.regle) {
    return `Réponse «\u00a0oui\u00a0» si ${phraseRegle(jeu.regle, libelles)}. Sinon «\u00a0non\u00a0».`;
  }
  if (jeu.niveau === 'CHAMPS') {
    return `Valeur de : ${(jeu.champs ?? []).map((champ) => libelles[champ] ?? champ).join(', ')}.`;
  }
  const document = jeu.document ?? '';
  return `Document «\u00a0${libelles[document] ?? document}\u00a0», au format PDF.`;
}

/** Ce que l'administrateur saisit dans l'assistant, étape après étape. */
export interface SaisieJeu {
  registre: string;
  niveau: Niveau | '';
  champ: string;
  operateur: Operateur | '';
  valeur: string;
  /** Informations transmises au niveau `CHAMPS`, séparées par des virgules. */
  champs: string;
  document: string;
  libelle: string;
  description: string;
  /** Fin du code, après le nom du registre : `majeur` pour `etat-civil.majeur`. */
  suffixe: string;
  validite_jours: string;
}

export const SAISIE_VIDE: SaisieJeu = {
  registre: '',
  niveau: '',
  champ: '',
  operateur: '',
  valeur: '',
  champs: '',
  document: '',
  libelle: '',
  description: '',
  suffixe: '',
  validite_jours: '90',
};

const TITRES_CONTENU: Record<Niveau | '', string> = {
  '': 'Règle',
  STATUT: 'Règle',
  CHAMPS: 'Informations transmises',
  DOCUMENT: 'Document',
};

/** Les cinq étapes de l'assistant ; la troisième porte le nom du contenu à définir. */
export function etapes(niveau: Niveau | ''): string[] {
  return ['Registre', 'Forme', TITRES_CONTENU[niveau], 'Libellé et durée', 'Publication'];
}

const NOM_TECHNIQUE = /^[a-z][a-z0-9_]*$/;
const SUFFIXE = /^[a-z0-9]+([.-][a-z0-9]+)*$/;
const TYPE_DOCUMENT = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const ENTIER = /^\d{1,3}$/;
const NOMBRE = /^-?\d+([.,]\d+)?$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const VALIDITE_MAXIMALE_JOURS = 3650;

function liste(texte: string): string[] {
  return texte
    .split(/[,\n]/)
    .map((element) => element.trim())
    .filter(Boolean);
}

/** Vérifie une étape de l'assistant ; un objet vide laisse passer à la suivante. */
export function verifierEtape(etape: number, saisie: SaisieJeu): Erreurs {
  const erreurs: Erreurs = {};
  if (etape === 1 && !saisie.registre) {
    erreurs['registre'] = 'Choisissez un registre.';
  }
  if (etape === 2 && !saisie.niveau) {
    erreurs['niveau'] = 'Choisissez une forme de réponse.';
  }
  if (etape === 3) {
    verifierContenu(saisie, erreurs);
  }
  if (etape === 4) {
    if (saisie.libelle.trim().length < 3) {
      erreurs['libelle'] = 'Saisissez le libellé. Exemple : Vous êtes majeur';
    }
    if (!SUFFIXE.test(saisie.suffixe.trim())) {
      erreurs['code'] = 'Saisissez un code en minuscules, chiffres et tirets. Exemple : majeur';
    }
    const jours = Number(saisie.validite_jours);
    if (
      !/^\d+$/.test(saisie.validite_jours.trim()) ||
      jours < 1 ||
      jours > VALIDITE_MAXIMALE_JOURS
    ) {
      erreurs['validite'] = `Saisissez un nombre de jours entre 1 et ${VALIDITE_MAXIMALE_JOURS}.`;
    }
  }
  return erreurs;
}

function verifierContenu(saisie: SaisieJeu, erreurs: Erreurs): void {
  if (saisie.niveau === 'CHAMPS') {
    const champs = liste(saisie.champs);
    if (champs.length === 0 || !champs.every((champ) => NOM_TECHNIQUE.test(champ))) {
      erreurs['champs'] = 'Choisissez au moins une information.';
    }
    return;
  }
  if (saisie.niveau === 'DOCUMENT') {
    if (!TYPE_DOCUMENT.test(saisie.document.trim())) {
      erreurs['document'] = 'Choisissez le document.';
    }
    return;
  }
  if (!NOM_TECHNIQUE.test(saisie.champ.trim())) {
    erreurs['regle.champ'] = 'Choisissez l’information à vérifier.';
  }
  const operateur = decrireOperateur(saisie.operateur);
  if (!operateur) {
    erreurs['regle.operateur'] = 'Choisissez une condition.';
    return;
  }
  const valeur = saisie.valeur.trim();
  const estUnAge = operateur.code === 'AGE_AU_MOINS' || operateur.code === 'AGE_AU_PLUS';
  if (estUnAge && !ENTIER.test(valeur)) {
    erreurs['regle.valeur'] = 'Saisissez un âge en chiffres. Exemple : 18';
  } else if (operateur.valeur === 'nombre' && !NOMBRE.test(valeur) && !DATE.test(valeur)) {
    erreurs['regle.valeur'] = 'Saisissez un nombre ou une date. Exemple : 12';
  } else if (operateur.valeur === 'texte' && !valeur) {
    erreurs['regle.valeur'] = 'Saisissez la valeur attendue.';
  }
}

function construireRegle(saisie: SaisieJeu): Regle {
  const operateur = saisie.operateur as Operateur;
  const regle: Regle = { champ: saisie.champ.trim(), operateur };
  const attendu = decrireOperateur(operateur)?.valeur;
  const valeur = saisie.valeur.trim();
  if (attendu === 'nombre') {
    regle.valeur = DATE.test(valeur) ? valeur : Number(valeur.replace(',', '.'));
  } else if (attendu === 'texte') {
    regle.valeur = valeur === 'true' || valeur === 'false' ? valeur === 'true' : valeur;
  }
  return regle;
}

/**
 * La question telle que le serveur l'attend. Seul le contenu du niveau choisi est envoyé :
 * une règle saisie puis abandonnée pour un autre niveau ne part pas.
 */
export function construireJeu(saisie: SaisieJeu, actif = true): Jeu {
  const niveau = saisie.niveau as Niveau;
  const description = saisie.description.trim();
  return {
    code: `${saisie.registre}.${saisie.suffixe.trim()}`,
    registre: saisie.registre,
    niveau,
    libelle: saisie.libelle.trim(),
    ...(description ? { description } : {}),
    validite: `P${Number(saisie.validite_jours)}D`,
    ...(niveau === 'STATUT' ? { regle: construireRegle(saisie) } : {}),
    ...(niveau === 'CHAMPS' ? { champs: liste(saisie.champs) } : {}),
    ...(niveau === 'DOCUMENT' ? { document: saisie.document.trim() } : {}),
    actif,
  };
}

const JOURS_PAR_UNITE: Record<string, number> = { D: 1, W: 7, M: 30, Y: 365 };

/** Nombre de jours d'une durée ISO simple (`P90D`, `P3M`, `P1Y`) ; `null` si elle est composée. */
export function joursDeValidite(validite: string): number | null {
  const lue = /^P(\d+)([DWMY])$/.exec(validite);
  return lue ? Number(lue[1]) * JOURS_PAR_UNITE[lue[2]] : null;
}

export function phraseValidite(validite: string): string {
  const jours = joursDeValidite(validite);
  if (jours === null) {
    return validite;
  }
  return jours === 1 ? '1 jour' : `${jours} jours`;
}

/** Remet une question existante dans l'assistant, pour la modifier. */
export function versSaisie(jeu: Jeu): SaisieJeu {
  return {
    ...SAISIE_VIDE,
    registre: jeu.registre,
    niveau: jeu.niveau,
    champ: jeu.regle?.champ ?? '',
    operateur: jeu.regle?.operateur ?? '',
    valeur: jeu.regle?.valeur === undefined ? '' : String(jeu.regle.valeur),
    champs: (jeu.champs ?? []).join(', '),
    document: jeu.document ?? '',
    libelle: jeu.libelle,
    description: jeu.description ?? '',
    suffixe: jeu.code.startsWith(`${jeu.registre}.`)
      ? jeu.code.slice(jeu.registre.length + 1)
      : jeu.code,
    validite_jours: String(joursDeValidite(jeu.validite) ?? SAISIE_VIDE.validite_jours),
  };
}

/** Ce que l'essai a renvoyé, en lignes lisibles. */
export function lignesEssai({ resultat }: ResultatEssai): string[] {
  if (typeof resultat.valeur === 'boolean') {
    return [`Réponse du registre : ${resultat.valeur ? 'oui' : 'non'}.`];
  }
  if (resultat.champs) {
    return Object.entries(resultat.champs).map(([champ, valeur]) => `${champ} : ${valeur}`);
  }
  if (resultat.document) {
    return [`Document reçu : ${resultat.document.nom} (${resultat.document.type}).`];
  }
  return ['Réponse vide.'];
}
