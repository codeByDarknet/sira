let compteur = 0;

/** Identifiant d'élément unique dans la page, pour relier libellés, aides et erreurs. */
export function identifiantUnique(prefixe: string): string {
  compteur += 1;
  return `${prefixe}-${compteur}`;
}
