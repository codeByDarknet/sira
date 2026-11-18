import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from './session';

/** Chemin de l'écran affiché quand le serveur est injoignable ou que le rôle manque. */
export const CHEMIN_INDISPONIBLE = '/indisponible';
export const CHEMIN_ACCES_REFUSE = '/acces-refuse';

/**
 * Route réservée à un usager connecté. Sans session, l'usager part se connecter et revient
 * sur l'adresse demandée. Ce contrôle est un confort d'affichage : le serveur revérifie tout.
 */
export const gardeConnecte: CanActivateFn = async (_route, etat) => {
  const session = inject(Session);
  const routeur = inject(Router);
  try {
    if (await session.charger()) {
      return true;
    }
  } catch {
    return routeur.parseUrl(CHEMIN_INDISPONIBLE);
  }
  session.seConnecter(etat.url);
  return false;
};

/** Route réservée à un rôle : `canActivate: [gardeConnecte, gardeRole('administrateur')]`. */
export function gardeRole(role: string): CanActivateFn {
  return () => inject(Session).aLeRole(role) || inject(Router).parseUrl(CHEMIN_ACCES_REFUSE);
}

/** Route réservée aux agents qui ont l'un des droits cités : `gardeDroit('PERSONNES_LIRE')`. */
export function gardeDroit(...permissions: string[]): CanActivateFn {
  return () => inject(Session).peut(...permissions) || inject(Router).parseUrl(CHEMIN_ACCES_REFUSE);
}
