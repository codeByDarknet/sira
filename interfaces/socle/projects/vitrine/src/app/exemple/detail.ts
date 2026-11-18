import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  DatePipe,
  Dialogue,
  Etiquette,
  Fil,
  Icone,
  Squelette,
  Synthese,
  SyntheseLigne,
} from '@sira/ui';
import { Demarches } from './demarches';

@Component({
  selector: 'vi-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Alerte,
    Bouton,
    DatePipe,
    Dialogue,
    Etiquette,
    Fil,
    Icone,
    Squelette,
    Synthese,
    SyntheseLigne,
  ],
  templateUrl: './detail.html',
})
export class Detail {
  private readonly demarches = inject(Demarches);
  private readonly routeur = inject(Router);

  /** Paramètre `:id` de la route, relié par le routeur. */
  readonly id = input.required<string>();

  protected readonly demarche = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.demarches.lire(params),
  });
  protected readonly probleme = computed(() =>
    this.demarche.error() ? lireProbleme(this.demarche.error()).message : null,
  );

  protected readonly confirmation = signal(false);
  protected readonly suppression = signal(false);
  protected readonly echec = signal<string | null>(null);

  protected supprimer(): void {
    this.suppression.set(true);
    this.demarches.supprimer(this.id()).subscribe({
      next: () =>
        void this.routeur.navigate(['/exemple'], {
          queryParams: { message: 'La démarche a été supprimée.' },
        }),
      error: (erreur: unknown) => {
        this.suppression.set(false);
        this.confirmation.set(false);
        this.echec.set(lireProbleme(erreur).message);
      },
    });
  }
}
