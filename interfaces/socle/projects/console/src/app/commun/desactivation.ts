import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Dialogue, Icone } from '@sira/ui';
import { Observable } from 'rxjs';
import { Annonces } from './annonces';

/**
 * Bouton « Désactiver » et sa confirmation. Le contenu projeté dit ce que la désactivation
 * change ; après coup, l'usager revient à la liste.
 */
@Component({
  selector: 'co-desactivation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Dialogue, Icone],
  template: `@if (echec(); as message) {
      <sira-alerte type="erreur" titre="Échec de la désactivation">{{ message }}</sira-alerte>
    }
    <button siraBouton variante="danger" type="button" (click)="ouvert.set(true)">
      <sira-icone nom="ban" />
      Désactiver
    </button>
    <sira-dialogue [titre]="question()" [(ouvert)]="ouvert">
      <ng-content />
      <p>L’historique est conservé.</p>
      <button
        dialogue-actions
        siraBouton
        variante="danger"
        type="button"
        [enCours]="enCours()"
        (click)="confirmer()"
      >
        Désactiver
      </button>
      <button
        dialogue-actions
        siraBouton
        variante="secondaire"
        type="button"
        (click)="ouvert.set(false)"
      >
        Annuler
      </button>
    </sira-dialogue>`,
})
export class Desactivation {
  private readonly routeur = inject(Router);
  private readonly annonces = inject(Annonces);

  /** Titre de la confirmation : « Désactiver ce membre ? ». */
  readonly question = input.required<string>();
  readonly appel = input.required<() => Observable<unknown>>();
  /** Liste où revenir, et message à y afficher. */
  readonly retour = input.required<string>();
  readonly annonce = input.required<string>();

  protected readonly ouvert = signal(false);
  protected readonly enCours = signal(false);
  protected readonly echec = signal<string | null>(null);

  protected confirmer(): void {
    this.enCours.set(true);
    this.appel()().subscribe({
      next: () => {
        this.annonces.laisser(this.annonce());
        void this.routeur.navigateByUrl(this.retour());
      },
      error: (erreur: unknown) => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.echec.set(lireProbleme(erreur).message);
      },
    });
  }
}
