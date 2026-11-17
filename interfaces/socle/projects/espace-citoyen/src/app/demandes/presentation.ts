import { DemandeAcces } from '@sira/ui';
import { Demande } from '../commun/modeles';

/** Noms des champs des registres, tels qu'un citoyen les lit. */
const CHAMPS: Record<string, string> = {
  nom: 'nom',
  prenoms: 'prénoms',
  date_naissance: 'date de naissance',
  lieu_naissance: 'lieu de naissance',
  sexe: 'sexe',
  serie: 'série',
  annee: 'année',
  mention: 'mention',
  etablissement: 'établissement',
  specialite: 'spécialité',
};

/**
 * « Informations transmises : nom, date_naissance » devient « Informations transmises : nom,
 * date de naissance ». Un champ inconnu garde sa clé, sans soulignés.
 */
export function champsEnClair(detail: string | undefined): string | undefined {
  const separateur = detail?.indexOf(' : ') ?? -1;
  if (!detail || separateur < 0) {
    return detail;
  }
  const champs = detail
    .slice(separateur + 3)
    .split(',')
    .map((champ) => champ.trim())
    .map((cle) => CHAMPS[cle] ?? cle.replaceAll('_', ' '));
  return `${detail.slice(0, separateur)} : ${champs.join(', ')}`;
}

/** Présente une demande de l'API au composant « demande d'accès » de la bibliothèque. */
export function presenter(demande: Demande): DemandeAcces {
  return {
    demandeur: { nom: demande.service.nom, organisme: demande.service.organisme },
    finalite: demande.demarche.finalite,
    informations: demande.jeux.map((jeu) => ({
      libelle: jeu.libelle,
      niveau: jeu.niveau,
      source: jeu.registre.nom,
      // Pour un oui ou non, la bibliothèque dit déjà que l'information elle-même ne part pas ;
      // pour un document, le libellé le nomme déjà. Restent les champs, nommés en clair.
      precision: jeu.niveau === 'CHAMPS' ? champsEnClair(jeu.detail) : undefined,
    })),
    dureeJours: demande.demarche.duree_autorisation_jours,
    // Le code n'est plus montré ici : le citoyen le recopie depuis l'écran du service.
    expireLe: demande.expire_le,
  };
}

/**
 * Adresse de retour vers le service après la décision. Elle vient du serveur, qui l'a
 * vérifiée ; on n'y navigue pourtant que si c'est bien une adresse web.
 */
export function adresseDeRetour(adresse: string | null | undefined): string | null {
  if (!adresse || !URL.canParse(adresse)) {
    return null;
  }
  const protocole = new URL(adresse).protocol;
  return protocole === 'https:' || protocole === 'http:' ? adresse : null;
}
