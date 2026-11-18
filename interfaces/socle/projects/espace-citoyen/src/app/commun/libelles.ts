import { NiveauInformation } from '@sira/ui';

/** Forme sous laquelle une information est transmise, dans les mots de l'usager. */
export const FORMES: Record<NiveauInformation, string> = {
  STATUT: 'Réponse oui ou non',
  CHAMPS: 'Quelques informations',
  DOCUMENT: 'Document PDF',
};

/** « 1 consultation », « 3 consultations » : le nombre et son nom accordé. */
export function compter(nombre: number, singulier: string, pluriel = `${singulier}s`): string {
  return `${nombre} ${nombre > 1 ? pluriel : singulier}`;
}
