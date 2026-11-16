import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Code de vérification que le citoyen recopie sur son téléphone pour autoriser. Affiché en
 * deux groupes de trois chiffres, et lu chiffre à chiffre par les lecteurs d’écran.
 */
@Component({
  selector: 'sira-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="sira-code__libelle">{{ libelle() }}</p>
    <p class="sira-code__chiffres">
      <span class="sira-masque">{{ lecture() }}</span>
      @for (groupe of groupes(); track $index) {
        <span aria-hidden="true">{{ groupe }}</span>
      }
    </p>`,
  host: { class: 'sira-code' },
})
export class Code {
  readonly code = input.required<string>();
  readonly libelle = input('Code de vérification');

  /** Deux groupes de trois chiffres : plus faciles à comparer d'un coup d'œil. */
  protected readonly groupes = computed(() => this.code().match(/.{1,3}/g) ?? []);
  /** Chiffres séparés, pour être lus un à un et non comme un nombre. */
  protected readonly lecture = computed(() => this.code().split('').join(' '));
}
