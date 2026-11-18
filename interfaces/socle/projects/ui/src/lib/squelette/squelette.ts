import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';

/** Squelette de chargement, immobile. Le texte est annoncé aux lecteurs d'écran. */
@Component({
  selector: 'sira-squelette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Skeleton],
  template: `<div class="sira-squelette" role="status">
    <span class="sira-masque">{{ libelle() }}</span>
    @for (ligne of rangees(); track ligne) {
      <p-skeleton
        styleClass="sira-squelette__ligne"
        aria-hidden="true"
        height="1.1rem"
        borderRadius="999px"
      />
    }
  </div>`,
})
export class Squelette {
  readonly lignes = input(3);
  readonly libelle = input('Chargement en cours');

  protected readonly rangees = computed(() => Array.from({ length: this.lignes() }, (_, i) => i));
}
