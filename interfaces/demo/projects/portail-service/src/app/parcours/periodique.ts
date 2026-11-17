import { DestroyRef, inject } from '@angular/core';

/**
 * Répète une action tant que l'écran est affiché. À appeler dans un constructeur. Rien n'est
 * demandé quand l'onglet est en arrière-plan : l'écran se rattrape au retour.
 */
export function periodiquement(millisecondes: number, action: () => void): void {
  const minuterie = setInterval(() => {
    if (document.visibilityState === 'visible') {
      action();
    }
  }, millisecondes);
  inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
}
