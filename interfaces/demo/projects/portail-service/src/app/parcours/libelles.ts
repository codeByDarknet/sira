import { EtatEtiquette } from '@sira/ui';
import { EtatDossier, Verification } from './parcours';

export interface Mention {
  texte: string;
  etat: EtatEtiquette;
}

const REGISTRES: Record<string, string> = {
  'etat-civil': 'État civil',
  nationalite: 'Nationalité',
  casier: 'Casier judiciaire',
  diplomes: 'Diplômes',
};

/** Nom du registre qui répond ; un registre inconnu de l'écran garde son code. */
export function nomRegistre(code: string): string {
  return REGISTRES[code] ?? code;
}

const DOSSIERS: Record<EtatDossier, Mention> = {
  EN_VERIFICATION: { texte: 'Vérification en cours', etat: 'attention' },
  COMPLET: { texte: 'Complet', etat: 'succes' },
  A_EXAMINER: { texte: 'À examiner', etat: 'info' },
};

export function mentionDossier(etat: EtatDossier): Mention {
  return DOSSIERS[etat];
}

const RECU: Record<Verification['niveau'], string> = {
  STATUT: 'Vérifié : oui',
  CHAMPS: 'Informations reçues',
  DOCUMENT: 'Document reçu',
};

/** Ce que le registre a répondu, dit en clair : la couleur ne fait que le souligner. */
export function mentionVerification(verification: Verification): Mention {
  switch (verification.etat) {
    case 'A_TRAITER':
      return { texte: 'En attente du registre', etat: 'attention' };
    case 'OBTENU':
    case 'REUTILISE':
      return verification.valeur === false
        ? { texte: 'Vérifié : non', etat: 'erreur' }
        : { texte: RECU[verification.niveau], etat: 'succes' };
    case 'INCONNU':
      return { texte: 'Inconnu du registre', etat: 'info' };
    default:
      return { texte: 'Non vérifié', etat: 'info' };
  }
}

const CHAMPS: Record<string, string> = {
  nom: 'Nom',
  prenoms: 'Prénoms',
  date_naissance: 'Date de naissance',
  lieu_naissance: 'Lieu de naissance',
  nom_pere: 'Nom du père',
  nom_mere: 'Nom de la mère',
  baccalaureat_moyenne: 'Moyenne au baccalauréat (sur 20)',
  baccalaureat_serie: 'Série du baccalauréat',
  baccalaureat_annee: 'Année du baccalauréat',
  baccalaureat_mention: 'Mention au baccalauréat',
  serie: 'Série',
  annee: 'Année',
  etablissement: 'Établissement',
  specialite: 'Spécialité',
};

/** Le registre nomme ses champs : un champ inconnu de l'écran s'affiche sous sa clé, mise en forme. */
export function libelleChamp(cle: string): string {
  const texte = cle.replaceAll('_', ' ');
  return CHAMPS[cle] ?? texte.charAt(0).toUpperCase() + texte.slice(1);
}

/** Une date reçue d'un registre se lit en clair : « 19 mai 2001 ». */
const DATE = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeZone: 'UTC' });

export function valeurChamp(valeur: string | number | boolean | null): string {
  if (typeof valeur === 'boolean') {
    return valeur ? 'Oui' : 'Non';
  }
  if (typeof valeur === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valeur)) {
    return DATE.format(new Date(`${valeur}T00:00:00Z`));
  }
  return valeur === null ? 'Non renseigné' : String(valeur);
}

/** Taille d'un fichier, en unités lisibles : « 840 octets », « 120 Ko », « 1,8 Mo ». */
const NOMBRE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });

export function tailleFichier(octets: number): string {
  if (octets < 1024) {
    return `${octets} octets`;
  }
  if (octets < 1024 * 1024) {
    return `${Math.round(octets / 1024)} Ko`;
  }
  return `${NOMBRE.format(octets / (1024 * 1024))} Mo`;
}

/** Une information du registre qui n'arrivera plus : la personne peut la remplacer par une pièce. */
export function nonTransmise(verification: Verification): boolean {
  return (
    verification.etat === 'REFUSE' ||
    verification.etat === 'INCONNU' ||
    verification.etat === 'EXPIRE'
  );
}

/** L'information est arrivée du registre, avec son attestation. */
export function recue(verification: Verification): boolean {
  return verification.etat === 'OBTENU' || verification.etat === 'REUTILISE';
}
