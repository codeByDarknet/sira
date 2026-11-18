import { Injectable, signal } from '@angular/core';

const FORMAT = /^\d{12}$/;

/** Retire les espaces que la personne a pu taper entre les groupes de chiffres. */
export function normaliserIdentifiant(saisie: string): string {
  return saisie.replaceAll(/\s/g, '');
}

export function identifiantValide(identifiant: string): boolean {
  return FORMAT.test(identifiant);
}

/**
 * L'identifiant unique saisi au début de la démarche, gardé en mémoire le temps de choisir
 * comment donner son accord. Il n'est écrit nulle part : un rechargement de la page l'efface.
 */
@Injectable({ providedIn: 'root' })
export class IdentifiantSaisi {
  private readonly valeur = signal<string | null>(null);

  readonly identifiant = this.valeur.asReadonly();

  retenir(identifiant: string): void {
    this.valeur.set(identifiantValide(identifiant) ? identifiant : null);
  }

  oublier(): void {
    this.valeur.set(null);
  }
}
