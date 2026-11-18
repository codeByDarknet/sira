import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/** Ouvre la caméra arrière, sans le son. Remplaçable dans les tests. */
export const CAMERA = new InjectionToken<() => Promise<MediaStream>>('CAMERA', {
  providedIn: 'root',
  factory: () => {
    // Hors contexte sécurisé (HTTP sur le réseau local), le navigateur n'expose pas `mediaDevices`.
    const appareils = inject(DOCUMENT).defaultView?.navigator.mediaDevices as
      MediaDevices | undefined;
    return () =>
      appareils?.getUserMedia({ video: { facingMode: 'environment' }, audio: false }) ??
      Promise.reject(new DOMException('Caméra inaccessible', 'NotFoundError'));
  },
});

export type PanneCamera = 'refusee' | 'absente' | 'indisponible';

/** Range l'erreur de `getUserMedia` dans l'un des trois cas expliqués à l'usager. */
export function qualifierPanne(erreur: unknown): PanneCamera {
  const nom = erreur instanceof DOMException ? erreur.name : '';
  if (nom === 'NotAllowedError' || nom === 'SecurityError') {
    return 'refusee';
  }
  // `NotSupportedError` : le navigateur ne voit aucun appareil de capture.
  const SANS_CAMERA = ['NotFoundError', 'OverconstrainedError', 'NotSupportedError'];
  return SANS_CAMERA.includes(nom) ? 'absente' : 'indisponible';
}
