import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Step, StepList, Stepper } from 'primeng/stepper';

/** Une étape : son nom court, et éventuellement une ligne qui dit ce qui s'y passe. */
export interface Etape {
  titre: string;
  detail?: string;
}

/**
 * Indicateur d'étapes d'un parcours court à ordre fixe, sur le Stepper horizontal de PrimeNG.
 * Chaque étape est nommée et numérotée ; les étapes passées sont cochées, l'étape en cours est
 * marquée (`aria-current="step"`, posé par PrimeNG). Les étapes ne se cliquent pas : on avance
 * dans le parcours, pas dans l'indicateur. Sous l'indicateur, l'étape en cours et ce qui s'y passe.
 */
@Component({
  selector: 'sira-etapes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Step, StepList, Stepper],
  template: `<nav class="sira-etapes" aria-label="Étapes de la démarche">
    <p-stepper class="sira-etapes__stepper" [value]="courante()" [linear]="true">
      <p-step-list>
        @for (etape of liste(); track $index) {
          @let numero = $index + 1;
          <p-step
            [value]="numero"
            [disabled]="true"
            [class.sira-etapes__etape--faite]="numero < courante()"
          >
            {{ etape.titre }}
            <span class="sira-masque">
              @if (numero < courante()) {
                (terminée)
              } @else if (numero > courante()) {
                (à venir)
              }
            </span>
          </p-step>
        }
      </p-step-list>
    </p-stepper>
    <p class="sira-etapes__position">
      Étape {{ courante() }} sur {{ liste().length }} · <strong>{{ enCours()?.titre }}</strong>
      @if (enCours()?.detail) {
        <span class="sira-etapes__detail"> · {{ enCours()?.detail }}</span>
      }
    </p>
  </nav>`,
})
export class Etapes {
  /** Noms des étapes, ou étapes avec leur ligne d'explication. */
  readonly etapes = input.required<readonly (string | Etape)[]>();
  /** Numéro de l'étape en cours, à partir de 1. */
  readonly courante = input.required<number>();

  protected readonly liste = computed<Etape[]>(() =>
    this.etapes().map((etape) => (typeof etape === 'string' ? { titre: etape } : etape)),
  );
  protected readonly enCours = computed<Etape | undefined>(() => this.liste()[this.courante() - 1]);
}
