import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * Bloc titré. Le niveau du titre suit le plan de la page (`niveauTitre`).
 * Emplacements : `[carte-etat]` à droite du titre, `[carte-actions]` en bas.
 */
@Component({
  selector: 'sira-carte',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `@if (titre()) {
      <div class="sira-carte__entete">
        <div class="sira-carte__titre" role="heading" [attr.aria-level]="niveauTitre()">
          {{ titre() }}
        </div>
        <ng-content select="[carte-etat]" />
      </div>
    }
    <div class="sira-carte__corps"><ng-content /></div>
    <div class="sira-carte__actions"><ng-content select="[carte-actions]" /></div>`,
  host: { class: 'sira-carte', '[class.sira-carte--teintee]': 'teintee()' },
})
export class Carte {
  readonly titre = input<string>();
  readonly niveauTitre = input<2 | 3 | 4>(2);
  readonly teintee = input(false, { transform: booleanAttribute });
}
