import { ChangeDetectionStrategy, Component, input, model, output, signal } from '@angular/core';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Dialogue } from '@sira/ui';
import { Observable } from 'rxjs';

/**
 * Confirmation d'une action difficile à annuler (désactiver, retirer), ouverte depuis un tableau.
 * L'appel n'est lancé qu'à la confirmation ; `fait` prévient l'écran, qui recharge sa liste.
 */
@Component({
  selector: 'co-confirmation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Dialogue],
  template: `<sira-dialogue
    [titre]="question()"
    [ouvert]="ouvert()"
    (ouvertChange)="fermer($event)"
  >
    <ng-content />
    @if (echec(); as message) {
      <sira-alerte type="erreur" titre="Action impossible">{{ message }}</sira-alerte>
    }
    <button
      dialogue-actions
      siraBouton
      variante="danger"
      type="button"
      [enCours]="enCours()"
      (click)="lancer()"
    >
      {{ confirmer() }}
    </button>
    <button dialogue-actions siraBouton variante="secondaire" type="button" (click)="fermer(false)">
      Annuler
    </button>
  </sira-dialogue>`,
})
export class Confirmation {
  readonly question = input.required<string>();
  readonly confirmer = input.required<string>();
  readonly ouvert = model(false);
  readonly appel = input.required<() => Observable<unknown>>();
  readonly fait = output();

  protected readonly enCours = signal(false);
  protected readonly echec = signal<string | null>(null);

  protected fermer(ouvert: boolean): void {
    if (!ouvert) {
      this.echec.set(null);
      this.ouvert.set(false);
    }
  }

  protected lancer(): void {
    this.enCours.set(true);
    this.echec.set(null);
    this.appel()().subscribe({
      next: () => {
        this.enCours.set(false);
        this.fait.emit();
        this.ouvert.set(false);
      },
      error: (erreur: unknown) => {
        this.enCours.set(false);
        this.echec.set(lireProbleme(erreur).message);
      },
    });
  }
}
