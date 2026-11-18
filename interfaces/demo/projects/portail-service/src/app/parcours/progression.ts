import { EtatEtiquette } from '@sira/ui';
import { Jeu, Verification } from './parcours';

/** Délai au-delà duquel un registre silencieux est dit « en attente » plutôt qu'« en cours ». */
export const DELAI_REPONSE_MS = 8000;

export interface Etape {
  jeu: string;
  libelle: string;
  registre: string;
  texte: string;
  etat: EtatEtiquette;
  /** Vrai tant que l'information peut encore arriver. */
  attendue: boolean;
}

/**
 * Où en est chaque information : en cours de récupération, reçue, attendue d'un registre qui ne
 * répond pas (le service réessaie seul), ou non transmise. Sans dossier encore, les jeux de la
 * démarche sont tous en cours ; ensuite, les lignes suivent les vérifications du dossier, seules à
 * faire foi (la description de la démarche peut dater de quelques minutes).
 */
export function progression(
  jeux: readonly Jeu[],
  verifications: readonly Verification[] | null,
  depuis: number,
): Etape[] {
  const lignes = verifications
    ? verifications.map((v) => ({
        jeu: v.jeu,
        libelle: v.libelle,
        registre: v.registre,
        etat: v.etat,
      }))
    : jeux.map((jeu) => ({
        jeu: jeu.code,
        libelle: jeu.libelle,
        registre: jeu.registre,
        etat: 'A_TRAITER' as const,
      }));
  return lignes.map(({ etat, ...base }) => {
    switch (etat) {
      case 'OBTENU':
      case 'REUTILISE':
        return { ...base, texte: 'Reçu', etat: 'succes', attendue: false };
      case 'A_TRAITER':
        return verifications && depuis >= DELAI_REPONSE_MS
          ? {
              ...base,
              texte: 'En attente : le registre ne répond pas, nous réessayons',
              etat: 'attention',
              attendue: true,
            }
          : { ...base, texte: 'En cours', etat: 'info', attendue: true };
      default:
        return { ...base, texte: 'Non transmis', etat: 'neutre', attendue: false };
    }
  });
}
