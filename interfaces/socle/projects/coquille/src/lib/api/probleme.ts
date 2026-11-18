import { HttpErrorResponse } from '@angular/common/http';

/** Erreur renvoyée par le serveur (RFC 9457, `application/problem+json`). */
export interface Probleme {
  statut: number;
  /** Message à afficher : ce qui s'est passé et quoi faire. */
  message: string;
  /** Erreurs par champ, quand le serveur refuse une saisie. */
  champs: Record<string, string>;
}

const MESSAGES: Record<number, string> = {
  0: 'Connexion impossible. Vérifiez votre réseau, puis réessayez.',
  403: 'Action non autorisée pour votre compte.',
  404: 'Élément introuvable.',
  409: 'Élément modifié entre-temps. Rechargez la page, puis réessayez.',
  429: 'Trop d’essais. Réessayez dans une minute.',
};
const MESSAGE_PAR_DEFAUT = 'Service indisponible, rien n’a été enregistré. Réessayez plus tard.';

/**
 * Traduit une erreur d'appel en message affichable. Le `detail` du serveur est repris
 * pour les refus (4xx) ; jamais pour une panne (5xx), dont le texte ne s'adresse pas à l'usager.
 */
export function lireProbleme(erreur: unknown): Probleme {
  if (!(erreur instanceof HttpErrorResponse)) {
    return { statut: 0, message: MESSAGE_PAR_DEFAUT, champs: {} };
  }
  const corps: unknown = erreur.error;
  const probleme =
    typeof corps === 'object' && corps !== null ? (corps as Record<string, unknown>) : {};
  const detail = typeof probleme['detail'] === 'string' ? probleme['detail'] : undefined;
  const refus = erreur.status >= 400 && erreur.status < 500;
  return {
    statut: erreur.status,
    message: (refus ? detail : undefined) ?? MESSAGES[erreur.status] ?? MESSAGE_PAR_DEFAUT,
    champs: lireChamps(probleme['erreurs']),
  };
}

/** `erreurs` : `[{ "champ": "nom", "message": "…" }]`. */
function lireChamps(erreurs: unknown): Record<string, string> {
  if (!Array.isArray(erreurs)) {
    return {};
  }
  const champs: Record<string, string> = {};
  for (const erreur of erreurs as { champ?: unknown; message?: unknown }[]) {
    if (typeof erreur.champ === 'string' && typeof erreur.message === 'string') {
      champs[erreur.champ] = erreur.message;
    }
  }
  return champs;
}
