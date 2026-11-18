import { Resource, ResourceRef } from '@angular/core';
import { Page, lireProbleme } from '@sira/coquille';

/** Éléments de la page chargée ; vide tant que la liste charge ou si elle a échoué. */
export function elements<T>(ressource: ResourceRef<Page<T> | undefined>): T[] {
  return ressource.hasValue() ? ressource.value().elements : [];
}

/** Valeur chargée, ou `undefined` : lire `value()` d'une ressource en erreur lèverait. */
export function valeur<T>(ressource: Resource<T | undefined>): T | undefined {
  return ressource.hasValue() ? ressource.value() : undefined;
}

/** Message à afficher si le chargement a échoué. */
export function messageErreur(ressource: Resource<unknown>): string | null {
  const erreur = ressource.error();
  // Une erreur qui n'est pas une `Error` arrive enveloppée, l'originale dans `cause`.
  return erreur ? lireProbleme(erreur.cause ?? erreur).message : null;
}
