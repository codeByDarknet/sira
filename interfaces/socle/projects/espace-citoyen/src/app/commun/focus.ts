import { DOCUMENT, inject } from '@angular/core';

/**
 * Quand l'écran change sans changer d'adresse (décision prise, accès retiré), le focus va
 * sur l'élément qui l'annonce, comme après une navigation.
 * À appeler dans un contexte d'injection.
 */
export function focusSur(): (selecteur: string) => void {
  const document = inject(DOCUMENT);
  return (selecteur) =>
    // L'élément n'est dans la page qu'après le rendu qui suit le changement d'état.
    setTimeout(() => {
      const cible = document.querySelector<HTMLElement>(selecteur);
      cible?.setAttribute('tabindex', '-1');
      cible?.focus();
    });
}
