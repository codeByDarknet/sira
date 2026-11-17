import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Bouton, Champ, Saisie } from '@sira/ui';
import { Demandes } from './demandes';

/**
 * Met un code court sous sa forme d'envoi (« klpm 4821 » → « KLPM-4821 »), ou répond `null`
 * s'il n'a pas huit lettres ou chiffres.
 */
export function normaliserCode(saisie: string): string | null {
  const code = saisie.toUpperCase().replace(/[\s-]/g, '');
  return /^[A-Z0-9]{8}$/.test(code) ? `${code.slice(0, 4)}-${code.slice(4)}` : null;
}

/** Saisie du code court affiché par un service : ouvre la demande correspondante. */
@Component({
  selector: 'ec-saisie-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton, Champ, Saisie],
  template: `<form novalidate (submit)="ouvrir($event, saisie.value)">
    <sira-champ
      [libelle]="libelle()"
      aide="Exemple : KLPM-4821"
      identifiant="code-court"
      [erreur]="erreur()"
    >
      <input
        #saisie
        siraSaisie
        class="sira-saisie--courte"
        name="code_court"
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        maxlength="12"
      />
    </sira-champ>
    <button siraBouton variante="secondaire" type="submit" [enCours]="envoi()">
      Ouvrir la demande
    </button>
  </form>`,
})
export class SaisieCode {
  private readonly demandes = inject(Demandes);
  private readonly routeur = inject(Router);

  readonly libelle = input('Saisir un code');

  protected readonly erreur = signal<string | null>(null);
  protected readonly envoi = signal(false);

  protected ouvrir(evenement: Event, saisie: string): void {
    evenement.preventDefault();
    const code = normaliserCode(saisie);
    if (!code) {
      this.erreur.set('Le code a 8 caractères. Exemple : KLPM-4821');
      return;
    }
    this.erreur.set(null);
    this.envoi.set(true);
    this.demandes.rattacherParCode(code).subscribe({
      next: (demande) => void this.routeur.navigate(['/accord', demande.id]),
      error: (erreur: unknown) => {
        this.envoi.set(false);
        const probleme = lireProbleme(erreur);
        this.erreur.set(
          probleme.statut === 404
            ? 'Ce code n’est pas reconnu, ou il a expiré. Vérifiez l’écran du service.'
            : probleme.message,
        );
      },
    });
  }
}
